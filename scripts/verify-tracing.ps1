param(
    [string]$GatewayUrl = 'http://localhost:8088',
    [string]$TempoUrl = 'http://localhost:3200'
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'Нужен PowerShell 7.'
}

function Assert-Status($Response, [int]$Expected, [string]$Label) {
    if ([int]$Response.StatusCode -ne $Expected) {
        throw "$Label : ожидался $Expected, получен $($Response.StatusCode)"
    }
}

$browser = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$loginPage = Invoke-WebRequest -WebSession $browser -Uri "$GatewayUrl/oauth2/authorization/keycloak"
$action = [Net.WebUtility]::HtmlDecode([regex]::Match($loginPage.Content,
    '<form id="kc-form-login"[^>]*action="([^"]+)"').Groups[1].Value)
if (-not $action) {
    throw 'Не найдена форма входа Keycloak. Проверьте запуск и realm.'
}

$loginCookies = ($loginPage.Headers['Set-Cookie'] | ForEach-Object { ($_ -split ';')[0] }) -join '; '
$null = Invoke-WebRequest -WebSession $browser -Method Post -Uri $action `
    -Headers @{Cookie=$loginCookies} -ContentType 'application/x-www-form-urlencoded' `
    -Body @{username='admin';password='admin'}
$null = $browser.Headers.Remove('Cookie')
$me = Invoke-RestMethod -WebSession $browser -Uri "$GatewayUrl/bff/me"
if ($me.name -ne 'admin') {
    throw 'Вход админа не завершился.'
}

$pets = Invoke-RestMethod -WebSession $browser -Uri "$GatewayUrl/api/pets"
if ($pets.Count -eq 0) {
    $csrf = Invoke-RestMethod -WebSession $browser -Uri "$GatewayUrl/bff/csrf"
    $headers = @{$csrf.headerName=$csrf.token}
    $owner = Invoke-RestMethod -WebSession $browser -Method Post -Uri "$GatewayUrl/api/owners" `
        -ContentType 'application/json' -Headers $headers -Body '{"name":"Trace Demo Owner", "age":41}'
    
    $petBody = @{petName='Trace Demo Pet';ageMonths=12;sex="MALE";species="FOX";breed="Огонь и лед";ownerId=$owner.id} | ConvertTo-Json
    $pet = Invoke-RestMethod -WebSession $browser -Method Post -Uri "$GatewayUrl/api/pets" `
        -ContentType 'application/json' -Headers $headers -Body $petBody
    $null = $browser.Headers.Remove($csrf.headerName)
} else {
    $pet = $pets[0]
}

$requestId = [guid]::NewGuid().ToString()
$traceId = [guid]::NewGuid().ToString('N')
$response = Invoke-WebRequest -WebSession $browser -Uri "$GatewayUrl/api/pets/$($pet.id)/review" `
    -Headers @{'X-Request-ID'=$requestId;traceparent="00-$traceId-00f067aa0ba902b7-01"} `
    -SkipHttpErrorCheck
Assert-Status $response 200 'Цепочка отзывов'
if (@($response.Headers['X-Request-ID']).Count -ne 1 -or $response.Headers['X-Request-ID'] -ne $requestId) {
    throw 'Request ID потерян или продублирован в ответе.'
}
if (($response.Content | ConvertFrom-Json).petId -ne $pet.id) {
    throw 'Неверный ответ отзывов.'
}
$null = $browser.Headers.Remove('traceparent')
$null = $browser.Headers.Remove('X-Request-ID')

$invalid = Invoke-WebRequest -WebSession $browser -Uri "$GatewayUrl/bff/me" `
    -Headers @{'X-Request-ID'='1-1-1-1-1'} -SkipHttpErrorCheck
Assert-Status $invalid 200 'Локальный endpoint BFF'
if ([string]$invalid.Headers['X-Request-ID'] -notmatch '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$') {
    throw 'Некорректный ID не заменен полным UUID.'
}
$null = $browser.Headers.Remove('X-Request-ID')
$denied = Invoke-WebRequest -WebSession $browser -Method Post -Uri "$GatewayUrl/api/owners" `
    -ContentType 'application/json' -Body '{"name":"No CSRF",age=41}' -SkipHttpErrorCheck
Assert-Status $denied 403 'CSRF'
if (-not $denied.Headers['X-Request-ID']) {
    throw 'На отказе безопасности нет Request ID.'
}

$missingRequestId = [guid]::NewGuid().ToString()
$missingpetId = [guid]::NewGuid().ToString()
$missing = Invoke-WebRequest -WebSession $browser `
    -Uri "$GatewayUrl/api/pets/$missingpetId/review" `
    -Headers @{'X-Request-ID'=$missingRequestId} -SkipHttpErrorCheck
Assert-Status $missing 404 'Несуществующий питомец'
$null = $browser.Headers.Remove('X-Request-ID')

$trace = $null
for ($attempt=0; $attempt -lt 15; $attempt++) {
    $result = Invoke-WebRequest -Uri "$TempoUrl/api/traces/$traceId" `
        -Headers @{Accept='application/json'} -SkipHttpErrorCheck
    if ($result.StatusCode -eq 200) {
        $trace = $result.Content | ConvertFrom-Json
        $batches = @($trace.batches) + @($trace.resourceSpans)
        $services = @($batches | ForEach-Object { $_.resource.attributes } |
            Where-Object {$_.key -eq 'service.name'} | ForEach-Object {$_.value.stringValue} | Sort-Object -Unique)
        $httpCount = @($batches | ForEach-Object { @($_.scopeSpans) + @($_.instrumentationexhibitionSpans) } |
            ForEach-Object { $_.spans } | Where-Object { $_.name -match '(?i)^http ' }).Count
        if (@('gateway-service','exhibition-service','review-service' |
                Where-Object {$_ -notin $services}).Count -eq 0 -and $httpCount -ge 5) {
                    break
        }
    }
    Start-Sleep -Seconds 2
}
if (-not $trace -or @('gateway-service','exhibition-service','review-service' |
        Where-Object {$_ -notin $services}).Count -gt 0) {
            throw 'Tempo не получил spans всех трех сервисов.'
}

function Convert-HexId([string]$Encoded) {
    [Convert]::ToHexString([Convert]::FromBase64String($Encoded)).ToLowerInvariant()
}
$httpSpans = @(foreach ($batch in $batches) {
    $service = ($batch.resource.attributes | Where-Object {$_.key -eq 'service.name'}).value.stringValue
    foreach ($scope in (@($batch.scopeSpans) + @($batch.instrumentationexhibitionSpans))) {
        foreach ($span in $scope.spans) {
            if ($span.name -match '(?i)^http ') {
                if ((Convert-HexId $span.traceId) -ne $traceId) {
                    throw 'В дереве разные trace ID.'
            }
                [pscustomobject]@{Service=$service;Kind=$span.kind;Id=$span.spanId;Parent=$span.parentSpanId}
            }
        }
    }
})
if (($httpSpans.Id | Sort-Object -Unique).Count -ne $httpSpans.Count) { throw 'Span ID дублируются.' }
$gatewayServer = $httpSpans | Where-Object {$_.Service -eq 'gateway-service' -and $_.Kind -eq 'SPAN_KIND_SERVER'}
$gatewayClient = $httpSpans | Where-Object {$_.Service -eq 'gateway-service' -and $_.Kind -eq 'SPAN_KIND_CLIENT'}
$exhibitionServer = $httpSpans | Where-Object {$_.Service -eq 'exhibition-service' -and $_.Kind -eq 'SPAN_KIND_SERVER'}
$exhibitionClient = $httpSpans | Where-Object {$_.Service -eq 'exhibition-service' -and $_.Kind -eq 'SPAN_KIND_CLIENT'}
$reviewServer = $httpSpans | Where-Object {$_.Service -eq 'review-service' -and $_.Kind -eq 'SPAN_KIND_SERVER'}
if ($httpSpans.Count -ne 5 -or (Convert-HexId $gatewayServer.Parent) -ne '00f067aa0ba902b7' -or
    $gatewayClient.Parent -ne $gatewayServer.Id -or $exhibitionServer.Parent -ne $gatewayClient.Id -or
    $exhibitionClient.Parent -ne $exhibitionServer.Id -or $reviewServer.Parent -ne $exhibitionClient.Id) {
    throw 'Нарушена parent-child цепочка пяти HTTP spans.'
}

$logDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'runtime-logs'
foreach ($service in @('gateway-service','exhibition-service','review-service')) {
    $path = Join-Path $logDirectory "$service.log"
    $lines = @(Get-Content -LiteralPath $path | Where-Object {
        $_ -match "request_id=$requestId" -and $_ -match "trace_id=$traceId"
    })
    if ($lines.Count -eq 0) {
        throw "В логах $service нет общего trace ID и request ID."
    }
}
if (Select-String -LiteralPath (Join-Path $logDirectory 'review-service.log') `
        -SimpleMatch $missingRequestId -Quiet) {
            throw 'Отсутствующий питомец вызвал downstream.'
}
[pscustomobject]@{Status='OK';RequestId=$requestId;TraceId=$traceId;HttpSpans=$httpSpans.Count;Services=($services -join ', ')}
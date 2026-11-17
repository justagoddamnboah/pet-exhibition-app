param([string]$GatewayUrl = 'http://localhost:8088')
$ErrorActionPreference = 'Stop'
$session = & "$PSScriptRoot/open-course-session.ps1" -GatewayUrl $GatewayUrl
$csrf = Invoke-RestMethod -WebSession $session "$GatewayUrl/bff/csrf"
$key = [guid]::NewGuid().ToString()
$name = "Idempotent Owner $key"
$age = 25
function Send-Owner([string]$Name, [Int]$age, [string]$Key, [bool]$IncludeCsrf = $true) {
    $headers = @{}
    if ($Key) { $headers['Idempotency-Key']=$Key }
    if ($IncludeCsrf) { $headers[$csrf.headerName]=$csrf.token }
    try {
        Invoke-WebRequest -WebSession $session -Method Post -Uri "$GatewayUrl/api/owners/idempotent" `
            -ContentType 'application/json' -Headers $headers -Body (@{name=$Name;age=$Age} | ConvertTo-Json) -SkipHttpErrorCheck
    } finally {
        $null = $session.Headers.Remove('Idempotency-Key')
        $null = $session.Headers.Remove($csrf.headerName)
    }
}
$first = Send-Owner $name $age $key
$repeat = Send-Owner $name $age $key
if ($first.StatusCode -ne 201 -or $repeat.StatusCode -ne 201) { throw 'Первый вызов и повтор должны вернуть 201.' }
if ($repeat.Headers['Idempotency-Replayed'] -ne 'true') { throw 'Повтор не распознан.' }
$a = $first.Content | ConvertFrom-Json
$b = $repeat.Content | ConvertFrom-Json
if ($a.id -ne $b.id -or $a.name -ne $b.name -or $a.age -ne $b.age) { throw 'Повтор изменил сохранённый результат.' }
if ((Send-Owner 'Different Owner' $age $key).StatusCode -ne 422) { throw 'Изменённое тело должно дать 422.' }
if ((Send-Owner $name $age '').StatusCode -ne 400) { throw 'Отсутствующий ключ должен дать 400.' }
if ((Send-Owner $name $age 'bad-key').StatusCode -ne 400) { throw 'Некорректный ключ должен дать 400.' }
if ((Send-Owner $name $age $key $false).StatusCode -ne 403) { throw 'Ключ не должен обходить CSRF.' }
$Owners = Invoke-RestMethod -WebSession $session "$GatewayUrl/api/owners"
if (@($Owners | Where-Object name -eq $name).Count -ne 1) { throw 'Создано больше одного хозяина.' }
[pscustomobject]@{Status='OK';OwnerId=$a.id;Key=$key;Replayed=$true;Mismatch=422;MissingKey=400;Csrf=403;Effects=1}
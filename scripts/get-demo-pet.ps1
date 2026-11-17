param(
    [Parameter(Mandatory)][Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [string]$GatewayUrl = 'http://localhost:8088'
)
$ErrorActionPreference = 'Stop'
$pets = Invoke-RestMethod -WebSession $Session -Uri "$GatewayUrl/api/pets"
if ($pets.Count -gt 0) { return $pets[0] }
$csrf = Invoke-RestMethod -WebSession $Session -Uri "$GatewayUrl/bff/csrf"
try {
    $headers = @{$csrf.headerName=$csrf.token}
    $owner = Invoke-RestMethod -WebSession $Session -Method Post -Uri "$GatewayUrl/api/owners" `
        -ContentType 'application/json' -Headers $headers -Body '{"name":"Retry Demo Owner","age":41}'
    $body = @{
                petName='Retry Demo Pet';
                ageMonths=9;
                sex='MALE';
                species='DOG';
                breed='Pudel';
                ownerId=$owner.id;
        } | ConvertTo-Json
    Invoke-RestMethod -WebSession $Session -Method Post -Uri "$GatewayUrl/api/pets" `
        -ContentType 'application/json' -Headers $headers -Body $body
} finally {
    $null = $Session.Headers.Remove($csrf.headerName)
}
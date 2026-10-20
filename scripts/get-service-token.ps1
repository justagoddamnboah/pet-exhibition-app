param([string]$Issuer = 'http://localhost:8085/realms/course')
$ErrorActionPreference = 'Stop'
$response = Invoke-RestMethod -Method Post -Uri "$Issuer/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' -Body @{
        grant_type = 'client_credentials'
        client_id = 'course-service-client'
        client_secret = 'course-service-secret'
    }

$response.access_token
# M3.4 manual sign-off helper (requires Prosys :53530, docker-compose.dev.yml up)
$ErrorActionPreference = "Stop"
$tok = if ($env:APP_BOOTSTRAP_TOKEN) { $env:APP_BOOTSTRAP_TOKEN } else { "dev-bootstrap-token" }

function Get-EdgeStatus {
    curl.exe -s "http://127.0.0.1:8081/api/edge/status" | ConvertFrom-Json
}

function Get-CentralConfig {
    curl.exe -s "http://localhost:8080/api/edge/INDORE/config" | ConvertFrom-Json
}

Write-Host "=== Sign-off 1: add ACTIVE tag ===" -ForegroundColor Cyan
$before = Get-CentralConfig
$edgeBefore = Get-EdgeStatus
Write-Host "Central version before: $($before.version)"
Write-Host "Edge fetched/applied before: $($edgeBefore.config.fetchedVersion) / $($edgeBefore.config.appliedVersion)"

$body = @'
{
  "siteId": "INDORE",
  "lineId": "LINE-01",
  "tagCode": "M34_SIGNOFF_01",
  "description": "M3.4 manual sign-off counter read",
  "protocol": "OPC_UA",
  "endpointUrl": "opc.tcp://localhost:53530/OPCUA/SimulationServer",
  "externalAddress": "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
  "dataType": "INT",
  "unit": null,
  "writable": false,
  "deadband": 0.0,
  "lifecycle": "ACTIVE"
}
'@

$tagFile = Join-Path $env:TEMP "m34-tag.json"
[System.IO.File]::WriteAllText($tagFile, $body)
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "http://localhost:8080/api/registry/tags" `
    -H "Content-Type: application/json" `
    -H "X-Setup-Token: $tok" `
    --data-binary "@$tagFile"

Write-Host "Waiting 20s for config poll + apply..."
Start-Sleep -Seconds 20

$after = Get-CentralConfig
$edgeAfter = Get-EdgeStatus
$tags = $edgeAfter.tags | ForEach-Object { $_.tagCode }
Write-Host "Central version after: $($after.version)"
Write-Host "Edge config: source=$($edgeAfter.config.source) fetched=$($edgeAfter.config.fetchedVersion) applied=$($edgeAfter.config.appliedVersion)"
Write-Host "Tags on edge: $($tags -join ', ')"
$hasNew = $tags -contains "M34_SIGNOFF_01"
Write-Host "Sign-off 1 new tag visible: $hasNew"
Write-Host "Sign-off 1 versions aligned: $($edgeAfter.config.fetchedVersion -eq $after.version -and $edgeAfter.config.appliedVersion -eq $after.version)"

Write-Host "`n=== Sign-off 2: central down, edge restart ===" -ForegroundColor Cyan
docker stop smart-factory-central-backend-1 | Out-Null
Start-Sleep -Seconds 2
docker restart smart-factory-plant-edge-1 | Out-Null
Write-Host "Waiting 25s for edge boot + OPC..."
Start-Sleep -Seconds 25
$edgeSnap = Get-EdgeStatus
Write-Host "Edge after restart: source=$($edgeSnap.config.source) lastError=$($edgeSnap.config.lastError) phase=$($edgeSnap.opcua.phase)"
Write-Host "Tag count: $($edgeSnap.tags.Count) (expect >= 5)"
$tagCount = ($edgeSnap.tags | Measure-Object).Count
Write-Host "Sign-off 2 LOCAL_SNAPSHOT: $($edgeSnap.config.source -eq 'LOCAL_SNAPSHOT')"
Write-Host "Sign-off 2 OPC UP with tags: $($edgeSnap.opcua.phase -eq 'UP' -and $tagCount -ge 5)"

Write-Host "`n=== Sign-off 3: central back ===" -ForegroundColor Cyan
docker start smart-factory-central-backend-1 | Out-Null
$deadline = (Get-Date).AddMinutes(2)
do {
    Start-Sleep -Seconds 3
    $h = curl.exe -s -o NUL -w "%{http_code}" "http://localhost:8080/actuator/health"
} while ($h -ne "200" -and (Get-Date) -lt $deadline)

Write-Host "Waiting 20s for poll..."
Start-Sleep -Seconds 20
$cfg = Get-CentralConfig
$edgeFinal = Get-EdgeStatus
Write-Host "Edge final: source=$($edgeFinal.config.source) fetched=$($edgeFinal.config.fetchedVersion) central=$($cfg.version)"
Write-Host "Sign-off 3 CENTRAL restored: $($edgeFinal.config.source -eq 'CENTRAL' -and $edgeFinal.config.fetchedVersion -eq $cfg.version)"
#Requires -Version 5.1
# Generates dev TLS material, Mosquitto passwd/acl, and seeds .env for docker-compose.dev.yml.
# Run from repo root: .\scripts\Generate-DevTls.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

$GitOpenSsl = "C:\Program Files\Git\usr\ssl\openssl.cnf"
if (Test-Path $GitOpenSsl) {
    $env:OPENSSL_CONF = $GitOpenSsl
}

$CentralUser = if ($env:MQTT_CENTRAL_USERNAME) { $env:MQTT_CENTRAL_USERNAME } else { "central-backend" }
$CentralPass = if ($env:MQTT_CENTRAL_PASSWORD) { $env:MQTT_CENTRAL_PASSWORD } else { "central-dev" }
$HealthUser = if ($env:MQTT_HEALTH_USERNAME) { $env:MQTT_HEALTH_USERNAME } else { "healthcheck" }
$HealthPass = if ($env:MQTT_HEALTH_PASSWORD) { $env:MQTT_HEALTH_PASSWORD } else { "healthcheck-dev" }
$EdgeIndoreUser = if ($env:MQTT_EDGE_USERNAME) { $env:MQTT_EDGE_USERNAME } else { "edge-INDORE" }
$EdgeIndorePass = if ($env:MQTT_EDGE_PASSWORD) { $env:MQTT_EDGE_PASSWORD } else { "edge-indore-dev" }
$EdgePunePass = if ($env:MQTT_EDGE_PUNE_PASSWORD) { $env:MQTT_EDGE_PUNE_PASSWORD } else { "edge-pune-dev" }

$CertsDir = Join-Path $RepoRoot "infra\certs"
$MosquittoDir = Join-Path $RepoRoot "infra\mosquitto"
New-Item -ItemType Directory -Force -Path $CertsDir | Out-Null

function Invoke-OpenSsl {
    param([string[]]$Args)
    & openssl @Args
    if ($LASTEXITCODE -ne 0) {
        throw "openssl failed: $($Args -join ' ')"
    }
}

# --- CA ---
$CaKey = Join-Path $CertsDir "ca.key"
$CaCrt = Join-Path $CertsDir "ca.crt"
if (-not (Test-Path $CaCrt)) {
    Invoke-OpenSsl @(
        "req", "-x509", "-newkey", "rsa:2048",
        "-keyout", $CaKey, "-out", $CaCrt,
        "-days", "825", "-nodes",
        "-subj", "/CN=Smart Factory Dev CA"
    )
}

function New-ServerCert {
    param(
        [string]$Name,
        [string[]]$SansDns,
        [string[]]$SansIp
    )
    $Key = Join-Path $CertsDir "$Name.key"
    $Csr = Join-Path $CertsDir "$Name.csr"
    $Crt = Join-Path $CertsDir "$Name.crt"
    $Ext = Join-Path $CertsDir "$Name.ext"

    $sanLines = @("subjectAltName = DNS:localhost")
    foreach ($d in $SansDns) { $sanLines += "DNS:$d" }
    foreach ($ip in $SansIp) { $sanLines += "IP:$ip" }
    $sanLines | Set-Content -Path $Ext -Encoding ascii

    Invoke-OpenSsl @(
        "req", "-newkey", "rsa:2048",
        "-keyout", $Key, "-out", $Csr,
        "-nodes", "-subj", "/CN=$Name"
    )
    Invoke-OpenSsl @(
        "x509", "-req", "-in", $Csr,
        "-CA", $CaCrt, "-CAkey", $CaKey, "-CAcreateserial",
        "-out", $Crt, "-days", "825",
        "-extfile", $Ext
    )
    Remove-Item -Force $Csr, $Ext -ErrorAction SilentlyContinue
}

New-ServerCert -Name "broker" -SansDns @("mosquitto", "localhost", "host.docker.internal") -SansIp @("127.0.0.1")
New-ServerCert -Name "caddy" -SansDns @("caddy", "localhost", "host.docker.internal") -SansIp @("127.0.0.1")

# --- Mosquitto passwd (docker) ---
$PasswdPath = Join-Path $MosquittoDir "passwd"
$PasswdContainer = "sf-dev-mosquitto-passwd-" + [guid]::NewGuid().ToString("n")
try {
    docker run --name $PasswdContainer eclipse-mosquitto:2 /bin/sh -c @"
mosquitto_passwd -b -c /tmp/mq.pass '$CentralUser' '$CentralPass' &&
mosquitto_passwd -b /tmp/mq.pass '$HealthUser' '$HealthPass' &&
mosquitto_passwd -b /tmp/mq.pass edge-INDORE '$EdgeIndorePass' &&
mosquitto_passwd -b /tmp/mq.pass edge-PUNE '$EdgePunePass'
"@
    if ($LASTEXITCODE -ne 0) {
        throw "mosquitto_passwd via docker failed"
    }
    docker cp "${PasswdContainer}:/tmp/mq.pass" $PasswdPath
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp mosquitto passwd failed"
    }
} finally {
    docker rm -f $PasswdContainer 2>$null | Out-Null
}
# Dev only: 644 so the mosquitto user can read the bind-mounted passwd on Windows Docker.
# On the cloud VM after copying passwd: chown 1883:1883 infra/mosquitto/passwd && chmod 600 (or 640).

# ACL
$AclPath = Join-Path $MosquittoDir "acl"
$AclExample = Join-Path $MosquittoDir "acl.example"
if (-not (Test-Path $AclPath) -and (Test-Path $AclExample)) {
    Copy-Item $AclExample $AclPath
}

# --- .env dev MQTT keys (preserve existing overrides) ---
$EnvPath = Join-Path $RepoRoot ".env"
$EnvExample = Join-Path $RepoRoot ".env.example"
if (-not (Test-Path $EnvPath) -and (Test-Path $EnvExample)) {
    Copy-Item $EnvExample $EnvPath
}
if (-not (Test-Path $EnvPath)) {
    New-Item -ItemType File -Path $EnvPath | Out-Null
}

$AdminPass = if ($env:APP_ADMIN_PASSWORD) { $env:APP_ADMIN_PASSWORD } else { "admin-dev" }
$EdgeConfigToken = if ($env:EDGE_CONFIG_TOKEN) { $env:EDGE_CONFIG_TOKEN } else { "sf_edge_indore_dev_token" }

$keys = @{
    "MQTT_CENTRAL_PASSWORD"      = $CentralPass
    "MQTT_PASSWORD"              = $EdgeIndorePass
    "MQTT_USERNAME"              = $EdgeIndoreUser
    "MQTT_EDGE_USERNAME"         = $EdgeIndoreUser
    "MQTT_EDGE_PASSWORD"         = $EdgeIndorePass
    "MQTT_HEALTH_PASSWORD"       = $HealthPass
    "APP_ADMIN_USERNAME"         = "admin@factory.local"
    "APP_ADMIN_PASSWORD"         = $AdminPass
    "APP_ADMIN_DISPLAY_NAME"     = "Dev Admin"
    "APP_EDGE_BOOTSTRAP_TOKENS"  = "INDORE:$EdgeConfigToken"
    "EDGE_CONFIG_TOKEN"          = $EdgeConfigToken
}

$content = Get-Content $EnvPath -Raw -ErrorAction SilentlyContinue
if ($null -eq $content) { $content = "" }
$lines = @()
if ($content.Length -gt 0) {
    $lines = $content -split "`r?`n"
}
$existing = @{}
foreach ($line in $lines) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=') {
        $existing[$Matches[1]] = $true
    }
}
$append = New-Object System.Collections.Generic.List[string]
foreach ($kv in $keys.GetEnumerator()) {
    if (-not $existing.ContainsKey($kv.Key)) {
        $append.Add("$($kv.Key)=$($kv.Value)")
    }
}
if ($append.Count -gt 0) {
    if ($lines.Count -gt 0 -and $lines[-1].Length -gt 0) {
        Add-Content -Path $EnvPath -Value ""
    }
    Add-Content -Path $EnvPath -Value ($append -join "`n")
}

Write-Host "Dev TLS generated under infra/certs; passwd and acl under infra/mosquitto."
Write-Host "Seeded .env with missing MQTT dev credentials (existing keys untouched)."
<#
.SYNOPSIS
  Demo prep: health checks, INLET_VALVE_OPEN toggle, valve ingest, registry NodeId PUT.

.EXAMPLE
  .\scripts\Prepare-Demo.ps1
  .\scripts\Prepare-Demo.ps1 -EnableInterlock -ValvePosition 2.0
  .\scripts\Prepare-Demo.ps1 -ValvePosition 75.5
  .\scripts\Prepare-Demo.ps1 -ClearInterlock
  .\scripts\Prepare-Demo.ps1 -NodeId "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;s=Valve101Pos" -TagCode VALVE_101_POS
#>
[CmdletBinding()]
param(
    [string] $BackendOrigin = "http://localhost:8080",
    [string] $EdgeStatusUrl = "http://127.0.0.1:8081/api/edge/status",
    [string] $SiteId = "INDORE",
    [string] $LineId = "LINE-01",
    [string] $TagCode = "VALVE_101_POS",
    [string] $SetpointTagCode = "REACTOR_TEMP_SP",
    [switch] $EnableInterlock,
    [switch] $ClearInterlock,
    [Nullable[double]] $ValvePosition,
    [string] $NodeId
)

$ErrorActionPreference = "Stop"

function Write-Info([string] $Message) {
    Write-Host $Message
}

function Write-WarnLine([string] $Message) {
    Write-Host $Message -ForegroundColor Yellow
}

function Invoke-CurlJson {
    param(
        [Parameter(Mandatory)] [string] $Method,
        [Parameter(Mandatory)] [string] $Url,
        [string] $Body
    )
    $tmp = $null
    try {
        $curlArgs = @("-sS", "-X", $Method, $Url, "-H", "Content-Type: application/json")
        if ($PSBoundParameters.ContainsKey("Body") -and $null -ne $Body) {
            # File body avoids PowerShell quote-stripping when invoking curl.exe -d.
            $tmp = [System.IO.Path]::GetTempFileName()
            [System.IO.File]::WriteAllText($tmp, $Body, [System.Text.UTF8Encoding]::new($false))
            $curlArgs += @("--data-binary", "@$tmp")
        }
        $raw = & curl.exe @curlArgs
        if ($LASTEXITCODE -ne 0) {
            throw "curl.exe failed ($LASTEXITCODE) for $Method $Url"
        }
        if ([string]::IsNullOrWhiteSpace($raw)) {
            return $null
        }
        return $raw | ConvertFrom-Json
    } finally {
        if ($tmp -and (Test-Path $tmp)) {
            Remove-Item -Force $tmp -ErrorAction SilentlyContinue
        }
    }
}

function Get-Tag {
    param(
        [string] $Site,
        [string] $Line,
        [string] $Code
    )
    $url = "$BackendOrigin/api/registry/tags?siteId=$([uri]::EscapeDataString($Site))&lineId=$([uri]::EscapeDataString($Line))"
    $tags = Invoke-CurlJson -Method GET -Url $url
    if ($null -eq $tags) {
        throw "No tags returned from $url"
    }
    $match = @($tags) | Where-Object { $_.tagCode -eq $Code } | Select-Object -First 1
    if ($null -eq $match) {
        throw "Tag $Code not found for $Site / $Line"
    }
    return $match
}

function ConvertTo-RegistryPutBody {
    param(
        $Tag,
        [string] $InterlockRuleKey,
        [string] $ExternalAddress,
        [switch] $SetInterlock,
        [switch] $SetExternalAddress
    )
    $resolvedInterlock = if ($SetInterlock) { $InterlockRuleKey } else { $Tag.interlockRuleKey }
    if ($null -eq $resolvedInterlock) { $resolvedInterlock = "" }
    $resolvedAddress = if ($SetExternalAddress) { $ExternalAddress } else { $Tag.externalAddress }
    $body = [ordered]@{
        siteId           = [string]$Tag.siteId
        lineId           = [string]$Tag.lineId
        tagCode          = [string]$Tag.tagCode
        description      = [string]$Tag.description
        protocol         = [string]$Tag.protocol
        endpointUrl      = [string]$Tag.endpointUrl
        externalAddress  = [string]$resolvedAddress
        dataType         = [string]$Tag.dataType
        unit             = $(if ($null -eq $Tag.unit) { $null } else { [string]$Tag.unit })
        writable         = [bool]$Tag.writable
        minValue         = $Tag.minValue
        maxValue         = $Tag.maxValue
        deadband         = [double]$Tag.deadband
        warningLow       = $Tag.warningLow
        warningHigh      = $Tag.warningHigh
        criticalLow      = $Tag.criticalLow
        criticalHigh     = $Tag.criticalHigh
        interlockRuleKey = [string]$resolvedInterlock
    }
    return ($body | ConvertTo-Json -Compress -Depth 6)
}

function Write-SharedNodeWarning {
    Write-WarnLine ""
    Write-WarnLine "SHARED NODE WARNING: INDORE and PUNE seed tags VALVE_101_POS, REACTOR_TEMP_C, and REACTOR_TEMP_SP share Prosys SimulationNodes (nsu=…;i=1002/1007)."
    Write-WarnLine "After pointing VALVE_101_POS or REACTOR_TEMP_SP at a custom writable NodeId, switching sites still reads/writes that same physical node until each site has its own NodeId in edge YAML and registry externalAddress."
    Write-WarnLine "Keep OPC_UA_ENDPOINT and OPC_ENDPOINT_URL on the same host Prosys advertises (localhost vs machine name such as Tanmay)."
}

# --- Health checks ---
Write-Info "=== Demo health checks ==="

try {
    $compose = docker compose ps --format json 2>$null
    if ($LASTEXITCODE -eq 0 -and $compose) {
        Write-Info "Docker compose: reachable"
    } else {
        Write-WarnLine "Docker compose: not healthy or not running (docker compose up -d)"
    }
} catch {
    Write-WarnLine "Docker compose: unavailable ($($_.Exception.Message))"
}

try {
    $allTags = Invoke-CurlJson -Method GET -Url "$BackendOrigin/api/registry/tags"
    $indore = @($allTags | Where-Object { $_.siteId -eq "INDORE" })
    $indoreL1 = @($indore | Where-Object { $_.lineId -eq "LINE-01" })
    Write-Info ("Registry tags: total={0} INDORE={1} INDORE/LINE-01={2} (expect 7 / 4 / 3)" -f @($allTags).Count, $indore.Count, $indoreL1.Count)
} catch {
    Write-WarnLine "Backend registry: unreachable at $BackendOrigin ($($_.Exception.Message))"
}

try {
    $edge = Invoke-CurlJson -Method GET -Url $EdgeStatusUrl
    $opc = $edge.opcUa
    if ($null -eq $opc) { $opc = $edge.opcua }
    Write-Info ("Edge status: OK at {0}" -f $EdgeStatusUrl)
    if ($null -ne $opc) {
        Write-Info ("  OPC phase/endpoint: {0}" -f (($opc | ConvertTo-Json -Compress)))
    }
} catch {
    Write-WarnLine "Edge status: unreachable at $EdgeStatusUrl ($($_.Exception.Message))"
}

Write-Info ""
Write-Info "UI URLs:"
Write-Info "  Live:  http://localhost:3000/?siteId=$SiteId&lineId=$LineId"
Write-Info "  Audit: http://localhost:3000/audit?siteId=$SiteId&lineId=$LineId"
Write-Info "  Registry: http://localhost:3000/registry?siteId=$SiteId&lineId=$LineId"
Write-Info "  Edge diagnostics: http://127.0.0.1:8081/"

if ($EnableInterlock -and $ClearInterlock) {
    throw "Use only one of -EnableInterlock or -ClearInterlock"
}

# --- Interlock toggle ---
if ($EnableInterlock -or $ClearInterlock) {
    Write-Info ""
    Write-Info "=== Interlock on $SetpointTagCode ($SiteId / $LineId) ==="
    $sp = Get-Tag -Site $SiteId -Line $LineId -Code $SetpointTagCode
    $key = if ($EnableInterlock) { "INLET_VALVE_OPEN" } else { "" }
    $putBody = ConvertTo-RegistryPutBody -Tag $sp -InterlockRuleKey $key -SetInterlock
    $updated = Invoke-CurlJson -Method PUT -Url "$BackendOrigin/api/registry/tags/$($sp.id)" -Body $putBody
    $shown = if ($null -eq $updated.interlockRuleKey -or $updated.interlockRuleKey -eq "") { "<empty>" } else { $updated.interlockRuleKey }
    Write-Info ("interlockRuleKey now: {0}" -f $shown)
    if ($EnableInterlock) {
        Write-Info "Rejection setup: ingest VALVE_101_POS=2.0 then write REACTOR_TEMP_SP (expect INTERLOCK_REJECTED)."
        Write-WarnLine "Before a valid setpoint write, re-run with -ValvePosition 75.5 (or -ClearInterlock). A leftover 2.0 blocks the happy path."
    } else {
        Write-Info "Interlock cleared. Setpoint writes no longer require VALVE_101_POS >= 5."
    }
}

# --- Valve telemetry ---
if ($PSBoundParameters.ContainsKey("ValvePosition") -and $null -ne $ValvePosition) {
    Write-Info ""
    Write-Info "=== Ingest VALVE_101_POS = $ValvePosition (GOOD) ==="
    $ts = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $ingest = @{
        siteId    = $SiteId
        lineId    = $LineId
        timestamp = $ts
        readings  = @(
            @{
                tagCode         = "VALVE_101_POS"
                value           = [double]$ValvePosition
                quality         = "GOOD"
                sourceTimestamp = $ts
            }
        )
    } | ConvertTo-Json -Compress -Depth 6
    Invoke-CurlJson -Method POST -Url "$BackendOrigin/api/telemetry/ingest" -Body $ingest | Out-Null
    Write-Info "Ingested VALVE_101_POS=$ValvePosition for $SiteId / $LineId."
    if ([double]$ValvePosition -lt 5.0) {
        Write-WarnLine "Value < 5.0: use this for INTERLOCK_REJECTED demos. Before a valid setpoint write, run -ValvePosition 75.5 or -ClearInterlock."
    } else {
        Write-Info "Value >= 5.0: INLET_VALVE_OPEN (if enabled) should allow a setpoint write."
    }
}

# --- NodeId PUT ---
if (-not [string]::IsNullOrWhiteSpace($NodeId)) {
    Write-Info ""
    Write-Info "=== Registry externalAddress for $TagCode -> $NodeId ==="
    $tag = Get-Tag -Site $SiteId -Line $LineId -Code $TagCode
    $putBody = ConvertTo-RegistryPutBody -Tag $tag -ExternalAddress $NodeId -SetExternalAddress
    $updated = Invoke-CurlJson -Method PUT -Url "$BackendOrigin/api/registry/tags/$($tag.id)" -Body $putBody
    Write-Info ("Registry externalAddress now: {0}" -f $updated.externalAddress)
    Write-WarnLine "LOCAL EDGE YAML ONLY (do not commit unless SeedAlignmentTest is updated to the same node-id):"
    Write-WarnLine "  In plant-edge-service/src/main/resources/application.yml, set the matching tag node-id to:"
    Write-WarnLine "    node-id: $NodeId"
    Write-WarnLine "  Then restart plant-edge-service. FLOAT tags need a Float or Double OPC UA node (not Int32)."
}

Write-SharedNodeWarning
Write-Info ""
Write-Info "Done. See DEMO_AND_CONFIG_PLAYBOOK.md for the full dry-run checklist."

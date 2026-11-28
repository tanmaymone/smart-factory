# Demo and Config Playbook

Practical dry-run checklist and configuration guide for the Smart Factory stack: TimescaleDB + MQTT, Prosys OPC UA, plant-edge service, Spring Boot backend, and Next.js UI.

For **empty-DB plant onboarding**, downloadable tag templates, and the five-domain pre-cutover checklist, use [PLANT_ONBOARDING_PLAYBOOK.md](PLANT_ONBOARDING_PLAYBOOK.md).

Use `curl.exe` in PowerShell so the shell does not invoke the `Invoke-WebRequest` alias named `curl`. Prefer `--data-binary "@file.json"` (or `.\scripts\Prepare-Demo.ps1`) for request bodies; inline `-d "{...}"` is easy to mangle under PowerShell quoting.

Helper script (health checks, interlock toggle, valve ingest, NodeId PUT):

```powershell
.\scripts\Prepare-Demo.ps1
.\scripts\Prepare-Demo.ps1 -EnableInterlock -ValvePosition 2.0
.\scripts\Prepare-Demo.ps1 -ValvePosition 75.5
.\scripts\Prepare-Demo.ps1 -ClearInterlock
.\scripts\Prepare-Demo.ps1 -NodeId "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;s=Valve101Pos" -TagCode VALVE_101_POS
```

---

## 1. Full End-to-End Dry Run Checklist

### 1.1 Start order and pass criteria

| Step | What | Pass check |
|------|------|------------|
| 1 | Docker (TimescaleDB + Mosquitto) | Containers healthy; Timescale extension present |
| 2 | Prosys OPC UA Simulation Server | Endpoint reachable; security **None + Anonymous** |
| 3 | Central backend `:8080` | Tag counts 7 / 4 / 3 |
| 4 | Plant edge + diagnostics UI `:8081` | OPC connected; seven tag rows |
| 5 | Next.js UI `:3000` | Live console loads; connection pill healthy |

#### Step 1 — Docker

```powershell
cd c:\Users\tanma\Coding\smart-factory
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# For registry writes and Prepare-Demo.ps1 tag PUTs, set the same bootstrap token for backend and Next:
# $env:APP_BOOTSTRAP_TOKEN = "dev-bootstrap-token"
# $env:NEXT_PUBLIC_BOOTSTRAP_TOKEN = "dev-bootstrap-token"
docker compose up -d
docker compose ps
docker compose exec timescaledb psql -U smartfactory -d smart_factory -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'timescaledb';"
```

Pass: both services healthy; one TimescaleDB row. Ports: Postgres `5432`, MQTT `1883`.

#### Step 2 — Prosys OPC UA Simulation Server

1. Start [Prosys OPC UA Simulation Server](https://prosysopc.com/products/opc-ua-simulation-server/).
2. Default endpoint: `opc.tcp://localhost:53530/OPCUA/SimulationServer`.
3. If the status bar / Endpoints view shows a machine hostname (for example `opc.tcp://Tanmay:53530/OPCUA/SimulationServer`), set **both** of these to that **same** URL before starting either Java app, and keep them aligned across restarts:

```powershell
$env:OPC_UA_ENDPOINT = "opc.tcp://Tanmay:53530/OPCUA/SimulationServer"
$env:OPC_ENDPOINT_URL = "opc.tcp://Tanmay:53530/OPCUA/SimulationServer"
```

4. Security must remain **None + Anonymous**. The edge client selects only that mode.

#### Step 3 — Central backend

```powershell
cd central-backend
.\mvnw.cmd spring-boot:run
```

In another terminal:

```powershell
curl.exe -s "http://localhost:8080/api/registry/tags" | Findstr /C:"tagCode"
curl.exe -s "http://localhost:8080/api/registry/tags?siteId=INDORE"
curl.exe -s "http://localhost:8080/api/registry/tags?siteId=INDORE&lineId=LINE-01"
```

Pass: **7** tags total, **4** for `INDORE`, **3** for `INDORE` + `LINE-01` on a clean seed. Extra tags created in prior demos (for example a leftover Registry test tag) raise these counts; the seven seed codes above must still be present.

#### Step 4 — Plant edge + Edge UI

Prefer Prosys running before edge start. If the server is down, the edge stays up in BACKOFF, shows a start-Prosys banner on `:8081`, and connects once the port accepts. Bad NodeIds / Bad initial reads still fail startup.

```powershell
cd plant-edge-service
.\mvnw.cmd spring-boot:run
```

Open `http://127.0.0.1:8081/` or:

```powershell
curl.exe -s "http://127.0.0.1:8081/api/edge/status"
```

Pass: OPC UA phase connected; seven tag observations; MQTT telemetry/command clients up when the broker is up.

#### Step 5 — Next.js UI

```powershell
cd web-frontend
npm install
npm run dev
```

Open `http://localhost:3000/?siteId=INDORE&lineId=LINE-01`. Override backend with `BACKEND_ORIGIN` only if it is not `http://localhost:8080`.

Pass: Live Telemetry table populated (or empty-but-registered rows); header connection pill not at-capacity / disconnected.

Optional one-shot health after all services are up:

```powershell
.\scripts\Prepare-Demo.ps1
```

#### Containerized alternative

Same data path, processes in images. Prosys stays on the host. From the repo root, with Docker Desktop already running:

```powershell
# Laptop: cloud unit + edge. OPC UA via host.docker.internal.
docker compose -f docker-compose.dev.yml up -d --build

# Cloud VM only (no edge):
docker compose -f docker-compose.cloud.yml up -d --build

# Plant PC only. Set the cloud broker and the PLC/simulator URL first.
# $env:MQTT_BROKER_URL = "tcp://<cloud-host>:1883"
# $env:OPC_UA_ENDPOINT = "opc.tcp://<plc-host>:53530/OPCUA/SimulationServer"
# $env:EDGE_MQTT_CLIENT_ID = "plant-edge-indore"
docker compose -f docker-compose.edge.yml up -d --build
```

UI: `http://localhost:3000/?siteId=INDORE&lineId=LINE-01`. Edge diagnostics: `http://127.0.0.1:8081/`. A container cannot use `localhost` for Prosys; use `host.docker.internal` or set `OPC_UA_ENDPOINT`.

### 1.2 Sites, lines, and the shared-node warning

Use the header **Site** / **Line** switcher, or the query string:

| URL | Tags |
|-----|------|
| `/?siteId=INDORE&lineId=LINE-01` | `VALVE_101_POS`, `REACTOR_TEMP_C`, `REACTOR_TEMP_SP` |
| `/?siteId=INDORE&lineId=LINE-02` | `VALVE_201_POS` only |
| `/?siteId=PUNE&lineId=LINE-01` | Same three codes as Indore Line 1 |

Seeded bounds: valves `0..100`, setpoint `15..90`. `REACTOR_TEMP_C` is not writable. `INLET_VALVE_OPEN` is **not** seeded (`interlockRuleKey` is null until you enable it).

**Shared-node warning:** INDORE and PUNE `REACTOR_TEMP_C` share Prosys Random `nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002` (read-only). Every seeded writable tag (`VALVE_101_POS`, `REACTOR_TEMP_SP`, `VALVE_201_POS` on both sites) shares Prosys Constant `nsu=…;i=1007`, the only built-in writable Double. A write to any of them changes that one value. Counter `i=1001` is Int32 and read-only. Store plant addresses as `nsu=<uri>;(i|s)=…` — never a non-zero `ns=` index. Give a tag its own NodeId in both edge YAML and registry `externalAddress` when it must be independent. The free edition restores the project from autosave, so a hand-edited nodeset does not stick.

### 1.3 How to read failures (do not confuse these)

| What you see | Meaning |
|--------------|---------|
| Immediate modal error, HTTP **422**, audit `REJECTED_VALIDATION` or `REJECTED_INTERLOCK` | Central stopped the write. **No** MQTT command, **no** OPC UA write. `NODE_TYPE_MISMATCH` and `NODE_NOT_WRITABLE` are this row when the latest sample already shows the OPC UA type or access level will reject the write. |
| HTTP **202**, modal `VALIDATED_DISPATCHED`, then SSE `COMMAND_UPDATE` with status **`FAILED_EDGE`** (reason such as `Bad_NotWritable` or Float/Double coerce miss) | Validation passed; the edge OPC UA write failed. Audit ends `FAILED_EDGE`. |
| Live table quality badge **BAD** (rose) or **UNCERTAIN** (amber); Alarm banner amber **Frozen** if an alarm was already active | Quality / alarm freeze path. Latest value can still update. Not by itself a command failure. `INLET_VALVE_OPEN` rejects a setpoint when same-line `VALVE_101_POS` quality is not GOOD. |

### 1.4 Rejection checks (central 422)

Stay on Live → `INDORE` / `LINE-01`.

#### Out of range

1. Click **Write** on `VALVE_101_POS`.
2. Target `150`, operator id, reason, confirm, **Submit command**.

Expect: HTTP 422 `TARGET_OUT_OF_RANGE`, audit `REJECTED_VALIDATION`, modal error, no `ACKED`.

API equivalent (PowerShell — write JSON to a file so quoting stays intact):

```powershell
# Provision once (bootstrap admin). APP_BOOTSTRAP_TOKEN must match the backend.
$tok = $env:APP_BOOTSTRAP_TOKEN
curl.exe -s -X POST "http://localhost:8080/api/access/actors" -H "Content-Type: application/json" -H "X-Setup-Token: $tok" -d "{\"actorId\":\"demo@factory.com\",\"displayName\":\"Demo Operator\",\"role\":\"OPERATOR\"}"
curl.exe -s -X POST "http://localhost:8080/api/access/actors" -H "Content-Type: application/json" -H "X-Setup-Token: $tok" -d "{\"actorId\":\"supervisor@factory.com\",\"displayName\":\"Demo Supervisor\",\"role\":\"SUPERVISOR\"}"

$oor = Join-Path $env:TEMP 'sf-oor.json'
[System.IO.File]::WriteAllText($oor, '{"siteId":"INDORE","lineId":"LINE-01","tagCode":"VALVE_101_POS","targetValue":150.0,"requestedBy":"spoofed@factory.com","reason":"out-of-range dry run"}')
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "http://localhost:8080/api/commands" -H "Content-Type: application/json" -H "X-Actor-Id: demo@factory.com" --data-binary "@$oor"
```

A wrong `X-Setup-Token` returns 401 even when `X-Actor-Id` is valid. Either provisioned id can submit a governed write; neither can `POST` registry.

When the edge is connected to Prosys, live telemetry can overwrite an ingested `VALVE_101_POS` within a second. For interlock demos, run `-ValvePosition` and the setpoint write immediately afterward, or rely on the live sim value if it is already below / above 5.

#### Interlock blocked

```powershell
.\scripts\Prepare-Demo.ps1 -EnableInterlock -ValvePosition 2.0
```

Then Write `REACTOR_TEMP_SP` = `40` (or the same via `POST /api/commands`).

Expect: HTTP 422 `INTERLOCK_REJECTED`, audit `REJECTED_INTERLOCK`. Rule `INLET_VALVE_OPEN` requires same site/line cached `VALVE_101_POS` GOOD and finite `>= 5.0`.

#### Reset before a valid setpoint write

A leftover `2.0` sample keeps blocking setpoint writes. Before the happy-path setpoint:

```powershell
.\scripts\Prepare-Demo.ps1 -ValvePosition 75.5
# or clear the rule entirely:
.\scripts\Prepare-Demo.ps1 -ClearInterlock
```

Valve writes (`VALVE_101_POS` = `55`) do **not** consult `INLET_VALVE_OPEN`.

### 1.5 Valid write with live ACK

Seeded writable tags use Prosys Constant `nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007` (Double, CurrentWrite set). Counter `i=1001` is Int32 and read-only. Random / waveform nodes are read-only Doubles. A FLOAT tag on either of those is rejected in the write modal and by `POST /api/commands` (`NODE_TYPE_MISMATCH` or `NODE_NOT_WRITABLE`) once a live sample has arrived. The edge coercer still refuses the write if that sample is missing.

Restart the edge and the central backend together after this seed change. Reconcile moves a tag off known seed indexes (`ns=3;i=1001|1002|1003|1004|1007`) onto the seeded `nsu=` form and leaves hand-edited addresses alone (they fail at the edge if they still use a non-zero `ns=` index).

#### Make a writable Double in Prosys

1. Objects view → Add → **New Variable**.
2. DataType **Double**, Value Type **Constant**, **Read Only unchecked**.
3. Copy the NodeId from Address Space view (Expert Mode) as an `nsu=` string, for example `nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;s=Valve101Pos`.
4. Alternative: use a variable under **StaticData**; copy its NodeId (do not guess it).

FLOAT registry tags coerce only to OPC UA **Float** or **Double**. Do not map a FLOAT tag onto an Int32 Counter node.

#### Point the demo tag (both layers)

Registry (script updates only `externalAddress`. Restart reconcile moves known seed `ns=3;i=…` addresses onto the seeded `nsu=` constant, and leaves any other address alone.):

```powershell
.\scripts\Prepare-Demo.ps1 -NodeId "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;s=Valve101Pos" -TagCode VALVE_101_POS -SiteId INDORE -LineId LINE-01
```

The registry `externalAddress` is the only place to change this since M3.4 — the edge re-reads it on the next config poll, swaps the monitored item, and re-seeds the deadband baseline for that tag. No YAML edit and no edge restart.

Remember the shared-node warning: the same NodeId on PUNE's `VALVE_101_POS` still hits this physical node until PUNE's tag is given a distinct `externalAddress` in the registry.

#### Happy-path valve write

1. Live → `INDORE` / `LINE-01` → Write `VALVE_101_POS` = `55`.
2. Expect modal `VALIDATED_DISPATCHED`, then SSE `COMMAND_UPDATE` status **`ACKED`** with `appliedValue`.
3. Confirm the same row on **Audit** (`/?siteId=INDORE&lineId=LINE-01` → Audit).
4. Confirm the command appears in edge UI history at `http://127.0.0.1:8081/`.

#### Happy-path setpoint write

1. Reset interlock state: `.\scripts\Prepare-Demo.ps1 -ValvePosition 75.5` (or `-ClearInterlock`).
2. Write `REACTOR_TEMP_SP` = `50`. Expect `ACKED`. On the stock seed this also moves `VALVE_101_POS`, because both tags are Constant `nsu=…;i=1007`.

If the modal shows `NODE_NOT_WRITABLE` or `NODE_TYPE_MISMATCH`, the tag is still on a generator (or the edge and registry NodeIds disagree). Do not treat that as a successful dispatch.

---

## 2. Configuration Playbook

### 2.1 Add a new plant, line, or OPC UA tag list

New sites, lines, and tag lists are created through the registry API. Do not edit Java seed code for a new plant.

1. **Site and line** (idempotent; retries return 200 and leave the existing row):

```powershell
curl.exe -s -X POST "http://localhost:8080/api/registry/sites" -H "Content-Type: application/json" -d "{\"siteCode\":\"NASHIK\",\"name\":\"Nashik Plant\",\"timezone\":\"Asia/Kolkata\"}"
curl.exe -s -X POST "http://localhost:8080/api/registry/lines" -H "Content-Type: application/json" -d "{\"siteCode\":\"NASHIK\",\"lineCode\":\"LINE-01\",\"name\":\"Reactor Line 1\"}"
```

API-created sites/lines default to lifecycle `DRAFT`. Promote with `PUT` (`lifecycle` may jump `DRAFT` → `ACTIVE`). Live telemetry, alarms, and writes require the site, line, and tag all `ACTIVE`.

2. **Bulk tags** — `POST /api/registry/tags/batch` (`application/json` array or `{"tags":[...]}`, or `text/csv`). Batch rows default to `DRAFT`. Row-level errors stay in the 200 summary; structural CSV failures are 400.

```powershell
curl.exe -s -X POST "http://localhost:8080/api/registry/tags/batch" -H "Content-Type: application/json" -d "{\"tags\":[{\"siteId\":\"NASHIK\",\"lineId\":\"LINE-01\",\"tagCode\":\"REACTOR_TEMP_C\",\"description\":\"Reactor temperature\",\"protocol\":\"OPC_UA\",\"endpointUrl\":\"opc.tcp://localhost:53530/OPCUA/SimulationServer\",\"externalAddress\":\"nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002\",\"dataType\":\"FLOAT\",\"unit\":\"C\",\"writable\":false,\"deadband\":0.2}]}"
```

3. **Single tag on an existing line** — Registry UI (`/registry?siteId=INDORE&lineId=LINE-01`) or `POST /api/registry/tags` (defaults to `ACTIVE`).

Since M3.4 the edge allow-list comes from `GET /api/edge/{siteId}/config`, not YAML. One edge process = one `EDGE_SITE_ID` (default `INDORE`). PUNE needs a second process with its own `EDGE_SITE_ID=PUNE`, MQTT client id, server port, `EDGE_CONFIG_SNAPSHOT_PATH`, and `EDGE_BUFFER_PATH`. Activate the site, line, and tag, then wait one poll (`EDGE_CONFIG_POLL_MS`, default 15s) — no edge YAML edit and no container restart. Watch the **Central Config Sync** card on `http://127.0.0.1:8081/`: `fetched / applied version` should advance and the tag should appear in the tag table. A tag the server refuses (bad address, unknown namespace, Bad read, Bad monitored item) is listed under rejected and skipped; the rest of the line keeps streaming. Rejected tags retry on a registry revision bump (including a name-only site edit — `contentHash` may be unchanged), OPC reconnect, or edge restart — not on a bare 15s poll.

Match `dataType` to the OPC UA node (FLOAT → Float/Double node; INT → integer node).

With no active tag for the site, the edge reports phase `IDLE` rather than a fault: it is intentionally connected to nothing, and MQTT, commands, and diagnostics stay up.

4. **Demo seed baseline** (optional, repo INDORE/PUNE only): update [`RegistrySeedRunner.sampleTags`](central-backend/src/main/java/com/factory/central/registry/RegistrySeedRunner.java) and [`SeedAlignmentTest`](plant-edge-service/src/test/java/com/factory/edge/opcua/SeedAlignmentTest.java).

### 2.2 Adjust deadbands

| Layer | Effect |
|-------|--------|
| Registry `deadband` | Source of truth. The edge picks it up on the next config poll; filtering still happens on the edge. A deadband-only change does not touch the OPC UA subscription, so baselines survive. |
| Edge YAML `deadband` | Static fallback only (`EDGE_CONFIG_SYNC_ENABLED=false` plus an explicit `app.opcua.tags` list). Restart edge. |

Seed defaults: valves `0.5`, `REACTOR_TEMP_C` `0.2`, `REACTOR_TEMP_SP` `0.1`.

### 2.3 Adjust bounds and interlocks

Use Registry UI or `PUT /api/registry/tags/{id}` with the full tag body. Bounds are inclusive. These survive backend restart (seed reconcile only refreshes `endpoint_url` and alarm thresholds).

| Field | Notes |
|-------|-------|
| `minValue` / `maxValue` | Valve seed `0..100`; setpoint `15..90`. Out of range → `TARGET_OUT_OF_RANGE`. |
| `interlockRuleKey` | Only built-in rule: `INLET_VALVE_OPEN`. Set it on the **target** tag (usually `REACTOR_TEMP_SP`). Prerequisite is same site/line `VALVE_101_POS` GOOD finite `>= 5`. Clear with empty string / null via UI or `.\scripts\Prepare-Demo.ps1 -ClearInterlock`. |

```powershell
.\scripts\Prepare-Demo.ps1 -EnableInterlock
.\scripts\Prepare-Demo.ps1 -ClearInterlock
```

### 2.4 Switch from Prosys simulator to a live plant OPC UA endpoint

1. Set **both** env vars (or YAML equivalents) to the **same** plant URL, including hostname:

```powershell
$env:OPC_UA_ENDPOINT = "opc.tcp://plc-host:4840"
$env:OPC_ENDPOINT_URL = "opc.tcp://plc-host:4840"
```

- Edge: `app.opcua.endpoint-url` / `OPC_UA_ENDPOINT`
- Backend seed reconcile: `app.registry.opc-endpoint-url` / `OPC_ENDPOINT_URL` (updates existing seed tags’ `endpoint_url` on restart)

2. Replace every tag’s NodeId in **edge YAML** and registry `externalAddress` with plant `nsu=<namespaceUri>;(i|s)=…` addresses (never a non-zero `ns=` index). Give INDORE and PUNE **distinct** NodeIds before cutover so a shared simulator node is not carried onto the PLC.

3. Restart edge and backend. Confirm Edge UI OPC phase connected and Live console updates.

4. **Security:** `app.opcua.security.policy: None` + `identity: anonymous` is **local/dev only** (Prosys). For a secured server set `policy: Basic256Sha256` (SignAndEncrypt) and `pki-dir` / `OPC_UA_PKI_DIR` to a readable tree:

   ```
   pki-dir/
     own/cert.der
     own/private-key.pem
     trusted/certs/   # server certificate(s) — must be non-empty
     trusted/crl/
     issuer/certs/
     issuer/crl/
   ```

   Put the OPC UA server certificate in `trusted/certs`, and trust the edge client certificate on the server. An empty or unreadable trust dir fails startup (`IllegalStateException`) and does **not** fall back to None. Handshake rejection after PKI is present may BACKOFF like any connect failure. Username tokens are out of scope in this release.

5. **Topology:** one `plant-edge-service` process = one `EDGE_SITE_ID` + one OPC UA session for that site’s active manifest. A second plant (e.g. PUNE) means a second edge process with distinct `EDGE_SITE_ID`, `EDGE_MQTT_CLIENT_ID`, server port, `EDGE_CONFIG_SNAPSHOT_PATH`, and `EDGE_BUFFER_PATH`.

6. Software interlocks and bounds stay in the central command path. They do not replace PLC / ladder hardwired interlocks.

---

## 3. Quick reference

| Service | URL / port |
|---------|------------|
| TimescaleDB | `localhost:5432` |
| Mosquitto | `localhost:1883` |
| Central backend | `http://localhost:8080` |
| Edge diagnostics | `http://127.0.0.1:8081` |
| Next.js | `http://localhost:3000` |
| Prosys OPC UA | `opc.tcp://localhost:53530/OPCUA/SimulationServer` |

| MQTT topics | Direction |
|-------------|-----------|
| `site/{siteId}/line/{lineId}/telemetry` | Edge → central |
| `site/{siteId}/line/{lineId}/commands` | Central → edge |
| `site/{siteId}/line/{lineId}/commands/acks` | Edge → central |

| Command statuses | Where |
|------------------|--------|
| `REJECTED_VALIDATION` / `REJECTED_INTERLOCK` | Central 422 |
| `VALIDATED_DISPATCHED` | Accepted, MQTT published |
| `ACKED` | Edge write + readback OK |
| `FAILED_EDGE` | Edge / OPC failure after dispatch |

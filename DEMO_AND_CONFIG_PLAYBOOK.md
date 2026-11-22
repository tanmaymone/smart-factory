# Demo and Config Playbook

Practical dry-run checklist and configuration guide for the Smart Factory stack: TimescaleDB + MQTT, Prosys OPC UA, plant-edge service, Spring Boot backend, and Next.js UI.

Use `curl.exe` in PowerShell so the shell does not invoke the `Invoke-WebRequest` alias named `curl`. Prefer `--data-binary "@file.json"` (or `.\scripts\Prepare-Demo.ps1`) for request bodies; inline `-d "{...}"` is easy to mangle under PowerShell quoting.

Helper script (health checks, interlock toggle, valve ingest, NodeId PUT):

```powershell
.\scripts\Prepare-Demo.ps1
.\scripts\Prepare-Demo.ps1 -EnableInterlock -ValvePosition 2.0
.\scripts\Prepare-Demo.ps1 -ValvePosition 75.5
.\scripts\Prepare-Demo.ps1 -ClearInterlock
.\scripts\Prepare-Demo.ps1 -NodeId "ns=3;s=Valve101Pos" -TagCode VALVE_101_POS
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

**Shared-node warning:** The seed baseline maps INDORE and PUNE `VALVE_101_POS`, `REACTOR_TEMP_C`, and `REACTOR_TEMP_SP` onto the same Prosys nodes `ns=3;i=1001`–`1003`. Switching sites after you point `VALVE_101_POS` or `REACTOR_TEMP_SP` at a custom writable NodeId still reads and writes that **same physical node** until each site has its own NodeId in both edge YAML (`app.opcua.tags[].node-id`) and registry `externalAddress`.

### 1.3 How to read failures (do not confuse these)

| What you see | Meaning |
|--------------|---------|
| Immediate modal error, HTTP **422**, audit `REJECTED_VALIDATION` or `REJECTED_INTERLOCK` | Central stopped the write. **No** MQTT command, **no** OPC UA write. |
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
$oor = Join-Path $env:TEMP 'sf-oor.json'
[System.IO.File]::WriteAllText($oor, '{"siteId":"INDORE","lineId":"LINE-01","tagCode":"VALVE_101_POS","targetValue":150.0,"requestedBy":"demo@factory.com","reason":"out-of-range dry run"}')
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST "http://localhost:8080/api/commands" -H "Content-Type: application/json" --data-binary "@$oor"
```

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

Built-in Prosys Simulation Counter / Random / Waveform nodes are generators. Current Prosys forces **Read Only** on simulated signals, so seed NodeIds `ns=3;i=1001`–`1004` typically return `Bad_NotWritable`. After a valid dispatch you then see `FAILED_EDGE` — that is expected until you map a writable Double.

#### Make a writable Double in Prosys

1. Objects view → Add → **New Variable**.
2. DataType **Double**, Value Type **Constant**, **Read Only unchecked**.
3. Copy the NodeId from Address Space view (Expert Mode), for example `ns=3;s=Valve101Pos`.
4. Alternative: use a variable under **StaticData**; copy its NodeId (do not guess it).

FLOAT registry tags coerce only to OPC UA **Float** or **Double**. Do not map a FLOAT tag onto an Int32 Counter node.

#### Point the demo tag (both layers)

Registry (script updates only `externalAddress`; seed reconcile does **not** refresh NodeIds):

```powershell
.\scripts\Prepare-Demo.ps1 -NodeId "ns=3;s=Valve101Pos" -TagCode VALVE_101_POS -SiteId INDORE -LineId LINE-01
```

Edge YAML — edit locally, **do not commit** (`SeedAlignmentTest` pins `ns=3;i=1001`–`1004`):

```yaml
# plant-edge-service/src/main/resources/application.yml — matching tag only
- site-id: INDORE
  line-id: LINE-01
  tag-code: VALVE_101_POS
  node-id: ns=3;s=Valve101Pos   # local demo only
  deadband: 0.5
```

Restart the edge after the YAML change. Remember the shared-node warning: the same NodeId on PUNE’s `VALVE_101_POS` still hits this physical node until PUNE is given a distinct NodeId in YAML + registry.

#### Happy-path valve write

1. Live → `INDORE` / `LINE-01` → Write `VALVE_101_POS` = `55`.
2. Expect modal `VALIDATED_DISPATCHED`, then SSE `COMMAND_UPDATE` status **`ACKED`** with `appliedValue`.
3. Confirm the same row on **Audit** (`/?siteId=INDORE&lineId=LINE-01` → Audit).
4. Confirm the command appears in edge UI history at `http://127.0.0.1:8081/`.

#### Happy-path setpoint write

1. Reset interlock state: `.\scripts\Prepare-Demo.ps1 -ValvePosition 75.5` (or `-ClearInterlock`).
2. If the setpoint NodeId is still a sim generator, map `REACTOR_TEMP_SP` the same way (writable Double + YAML + registry).
3. Write `REACTOR_TEMP_SP` = `50`. Expect `ACKED` as above.

If Prosys has no writable Double yet, treat `FAILED_EDGE` / `Bad_NotWritable` after `VALIDATED_DISPATCHED` as the expected edge outcome for seed NodeIds.

---

## 2. Configuration Playbook

### 2.1 Add a new OPC UA tag / NodeId

Edge allow-list is YAML-only. A registry-only tag can pass central validation and still never be written at the plant.

1. **Edge** — add under `app.opcua.tags` in [`plant-edge-service/src/main/resources/application.yml`](plant-edge-service/src/main/resources/application.yml):

```yaml
- site-id: INDORE
  line-id: LINE-01
  tag-code: MY_NEW_TAG
  node-id: ns=3;s=MyNewTag
  deadband: 0.5
```

Restart the edge.

2. **Central registry** — Registry UI (`/registry?siteId=INDORE&lineId=LINE-01`) or:

```powershell
curl.exe -s -X POST "http://localhost:8080/api/registry/tags" -H "Content-Type: application/json" -d "{\"siteId\":\"INDORE\",\"lineId\":\"LINE-01\",\"tagCode\":\"MY_NEW_TAG\",\"description\":\"My new tag\",\"protocol\":\"OPC_UA\",\"endpointUrl\":\"opc.tcp://localhost:53530/OPCUA/SimulationServer\",\"externalAddress\":\"ns=3;s=MyNewTag\",\"dataType\":\"FLOAT\",\"unit\":\"%\",\"writable\":true,\"minValue\":0,\"maxValue\":100,\"deadband\":0.5,\"warningLow\":null,\"warningHigh\":null,\"criticalLow\":null,\"criticalHigh\":null,\"interlockRuleKey\":null}"
```

Match `dataType` to the OPC UA node (FLOAT → Float/Double node; INT → integer node).

3. **Permanent seed** (optional, for repo baseline): also update [`RegistrySeedRunner.sampleTags`](central-backend/src/main/java/com/factory/central/registry/RegistrySeedRunner.java) and [`SeedAlignmentTest`](plant-edge-service/src/test/java/com/factory/edge/opcua/SeedAlignmentTest.java).

### 2.2 Adjust deadbands

| Layer | Effect |
|-------|--------|
| Edge YAML `deadband` | Actual Change-of-Value filter before MQTT / buffer. Restart edge. |
| Registry `deadband` | Stored on the tag mapping; filtering still happens on the edge. |

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

2. Replace every tag’s NodeId in **edge YAML** and registry `externalAddress` with the plant NodeIds. Give INDORE and PUNE **distinct** NodeIds before cutover so a shared simulator node is not carried onto the PLC.

3. Restart edge and backend. Confirm Edge UI OPC phase connected and Live console updates.

4. **Security limit today:** the edge connects with **SecurityPolicy None + Anonymous** only. Sign / SignAndEncrypt and username tokens require code changes in `OpcUaClientFactory` (and trust-store wiring). Cutover stops at None + Anonymous until that exists.

5. **Topology:** one `plant-edge-service` process = one OPC UA session for every tag in its YAML. A second plant usually means a second edge process with a distinct `EDGE_MQTT_CLIENT_ID`.

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

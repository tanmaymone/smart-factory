# System Architecture: Smart Factory & Digital Logbook (Hybrid Edge + Multi-Site Cloud)

> **Purpose of this Document:** This file serves as the single source of truth and architectural blueprint for AI coding assistants (Cursor) and developers building the Smart Factory & Digital Logbook platform. Strictly adhere to the technology choices, safety guardrails, data contracts, and milestone sequencing defined below.

## 1. Executive Summary & Core Philosophy

The platform enables bidirectional communication (read telemetry, execute governed writes/setpoints, store historical analytics, and maintain a digital audit logbook) between industrial PLC/SCADA systems (primarily Siemens S7/OPC UA, plus Modbus TCP and BACnet/IP) and a multi-site cloud control plane.

### Key Architectural Guardrails

1. **Separation of Data Paths (No Direct DB Contention):** SCADA/Edge collectors **never** write directly to the core transactional database. High-frequency telemetry flows through an MQTT ingestion pipeline into a time-series store (TimescaleDB) and an in-memory/Redis latest-value cache.

2. **Governed Write Path (Safety First):** The UI **never** communicates directly with PLCs or edge workers. All write commands pass through the Central Spring Boot Backend for RBAC validation, min/max bounds verification, pluggable interlock evaluation, and immutable audit logging before being dispatched over MQTT to a specific plant edge worker.

3. **Hardware-Level Safety Fallback:** Software interlocks are a mandatory business/safety layer, **not** a substitute for PLC hardwired/ladder logic interlocks.

4. **WAN Resilience (Store-and-Forward):** Plant edge services must buffer telemetry locally (disk/embedded queue) during internet/VPN outages and replay messages in chronological order upon reconnection.

5. **Noise Reduction:** Edge collectors enforce per-tag deadband / Change-of-Value (COV) filtering to prevent network and disk I/O saturation.

## 2. High-Level Topology

```
+-----------------------------------------------------------------------------------+
|                               PLANT SITE (ON-PREMISE EDGE)                        |
|                                                                                   |
|  [Siemens S7 / PLCs / SCADA]                                                      |
|        |  ^                                                                       |
|        |  | (OPC UA / S7 / Modbus TCP / BACnet IP)                                |
|        v  |                                                                       |
|  [Edge Controller Service (Java/Spring Boot or Go)] <---> [Edge Local UI (Web)]   |
|        |  ^                                                                       |
|        |  | (Deadband Filter + Local Disk Store-and-Forward Buffer)               |
|        v  |                                                                       |
|  [Local MQTT Broker (Mosquitto / EMQX)]                                           |
+--------|--------------------------------------------------------------------------+
         |  ^
         |  | (TLS / VPN Bridge: Telemetry Up, Validated Commands Down)
         v  |
+--------|--------------------------------------------------------------------------+
|                               CENTRAL / CLOUD CONTROL PLANE                       |
|                                                                                   |
|  [Cloud MQTT Broker (EMQX / Mosquitto)]                                           |
|        |  ^                                                                       |
|        v  |                                                                       |
|  [Central Backend: Java Spring Boot (Spring Modulith)]                            |
|     |-- Telemetry Ingestor & Alarm Evaluator                                      |
|     |-- Command Validator, Interlock Engine & Audit Logger                        |
|     |-- Tag Registry & Multi-Site Config Manager                                  |
|     |---> [Redis (Hot Latest-Tag Cache)]                                          |
|     |---> [PostgreSQL + TimescaleDB (Hypertables + Relational Core/Audit)]        |
|        |  ^                                                                       |
|        |  | (SSE / WebSockets for Live Stream; REST for Commands & History)       |
|        v  |                                                                       |
|  [Frontend UI: Next.js (App Router, TypeScript)]                                  |
+-----------------------------------------------------------------------------------+

```

### Deploy units

Three images, two machines. Cloud never dials OPC UA / S7 / Modbus. The only cross-site link is MQTT (telemetry + command ACKs up, validated commands down). One edge container per plant (`EDGE_MQTT_CLIENT_ID` unique).

| File | Where | What |
| --- | --- | --- |
| `docker-compose.yml` | dev laptop | TimescaleDB + Mosquitto. Java + Next stay on the host (`mvnw`, `npm run dev`). |
| `docker-compose.cloud.yml` | cloud VM | Includes infra. Adds `central-backend` (`:8080`, `MQTT_BROKER_URL=tcp://mosquitto:1883`, heap `-Xmx512m`) and `web-frontend` (`:3000`). `BACKEND_ORIGIN=http://central-backend:8080` is baked into Next rewrites at image build and set again at runtime for the SSE proxy. Postgres `shared_buffers=256MB`, `shm_size` 512mb, container cap 1024M. |
| `docker-compose.edge.yml` | plant PC | `plant-edge-service` only. `SERVER_ADDRESS=0.0.0.0` inside the container; host publishes `127.0.0.1:8081` so diagnostics stay local. SQLite volume `/data` (`EDGE_BUFFER_PATH`). `MQTT_BROKER_URL` default `tcp://host.docker.internal:1883`. OPC default `opc.tcp://host.docker.internal:53530/OPCUA/SimulationServer` (`OPC_UA_ENDPOINT`, else `OPC_ENDPOINT_URL`). Heap `-Xmx384m`. |
| `docker-compose.dev.yml` | dev laptop | Cloud unit + edge on one compose network (`tcp://mosquitto:1883`). Prosys stays on the Windows host. |

Anonymous Mosquitto `:1883` is the dev broker. A real plant link stays on a VPN. Do not publish 1883 on the public internet. Paho clients have no username or TLS socket yet.

## 3. Industrial Protocol Integration Strategy

The platform categorizes industrial protocols into three integration tiers:

| Tier | Protocol | Integration Strategy | Recommended Library / Driver | 
| ----- | ----- | ----- | ----- | 
| **Tier 1: Native IP (Plug & Play)** | **OPC UA** (Preferred) | Direct Ethernet TCP/IP connection with subscriptions, encryption, and auth. | **Eclipse Milo** (Java) | 
| **Tier 1: Native IP (Plug & Play)** | **Modbus TCP/IP** | Direct Ethernet TCP/IP register read/write over Port `502`. | **Apache PLC4X** or **DigitalPetri Modbus / j2mod** | 
| **Tier 1: Native IP (Plug & Play)** | **Siemens S7 (Ethernet)** | Direct S7comm access over Industrial Ethernet (Port `102`). | **Apache PLC4X** | 
| **Tier 2: IP w/ Protocol Quirks** | **BACnet/IP** | UDP Port `47808` for HVAC/building automation objects. | **BACnet4J** or **Apache PLC4X** | 
| **Tier 2: Bridged Real-Time** | **PROFINET** | **Out of scope for direct JVM polling** due to microsecond determinism & GC pauses. Connect via the Siemens PLC's built-in OPC UA server or S7 interface over the same Ethernet cable. | Eclipse Milo / Apache PLC4X | 
| **Tier 3: Serial / Fieldbus** | **Modbus RTU (RS-485/232), PROFIBUS, BACnet MS/TP** | **Requires client-supplied hardware gateways** (e.g., Moxa/Advantech RTU-to-TCP gateway, Siemens IE/PB Link, Anybus) converting serial signals to Modbus TCP or OPC UA. | Standard Tier 1 drivers talking to Gateway IP | 

## 4. Repository & Application Structure

Structure the workspace as a clean monorepo containing the three deployable applications and shared infrastructure:

```
smart-factory-platform/
├── architecture.md                 # This architectural specification
├── docker-compose.yml              # Host-dev infra only: TimescaleDB + Mosquitto (Redis later)
├── docker-compose.cloud.yml        # Cloud unit: infra + central-backend + web-frontend
├── docker-compose.edge.yml         # Plant unit: plant-edge-service only (one container per plant)
├── docker-compose.dev.yml          # Laptop: cloud unit + edge; Prosys stays on the host
├── infra/
│   └── mosquitto/
│       └── mosquitto.conf          # Broker config (port 1883; websockets 9001 later)
├── central-backend/                # App 1: Java 21 + Spring Boot (Control Plane)
│   ├── pom.xml / build.gradle
│   └── src/main/java/com/factory/central/
│       ├── registry/               # Site, Line, TagMapping CRUD & seed data
│       ├── ingestion/              # MQTT subscriber, alarm evaluator, Timescale writer
│       ├── cache/                  # Redis / in-memory latest tag state store
│       ├── command/                # RBAC, bounds check, interlock engine, audit log, MQTT publisher
│       └── stream/                 # SSE / WebSocket live push endpoints
├── plant-edge-service/             # App 2: Plant Edge Collector + Thin Edge UI
│   ├── pom.xml / build.gradle      # (Java Spring Boot + Eclipse Milo / PLC4X)
│   └── src/main/
│       ├── java/com/factory/edge/
│       │   ├── driver/             # OPC UA (Milo), S7/Modbus (PLC4X) connectors
│       │   ├── filter/             # Per-tag deadband & Change-of-Value logic
│       │   ├── buffer/             # Persistent disk queue (SQLite/MapDB/RocksDB) for WAN outages
│       │   ├── mqtt/               # Telemetry publisher & command subscriber/ACK publisher
│       │   └── web/                # Local diagnostics REST API & lightweight status UI
│       └── resources/static/       # Thin HTML/JS Edge Diagnostics Dashboard
└── web-frontend/                   # App 3: Next.js (App Router) Multi-Site UI
    ├── package.json
    └── src/
        ├── app/
        │   ├── page.tsx            # Live Telemetry & Alarm Console (Site/Line switcher)
        │   ├── registry/page.tsx   # Tag & Connector Registry CRUD UI
        │   └── audit/page.tsx      # Immutable Command Audit Logbook
        ├── components/
        │   ├── WriteCommandModal.tsx # Governed setpoint modal with bounds & ACK tracking
        │   └── TelemetryTable.tsx
        └── hooks/
            └── useTelemetryStream.ts # SSE client hook with auto-reconnect

```

## 5. Technology Stack Breakdown

### 5.1 Central Control Plane (`central-backend`)

* **Language & Framework:** Java 21, Spring Boot 3.x (structured cleanly using **Spring Modulith** domain boundaries).

* **Persistence:**

  * **PostgreSQL 16 + TimescaleDB Extension:** Relational tables for `sites`, `lines`, `tag_mappings`, `alarms`, and `command_audit_logs`. TimescaleDB hypertables (with compression and retention policies) for `telemetry_history`.

  * **Redis (or Caffeine in-memory fallback for local single-node dev):** Sub-millisecond reads for current/latest tag values and active alarm states.

* **Messaging & Streaming:**

  * **Eclipse Paho MQTT v3 (`org.eclipse.paho.client.mqttv3` 1.2.5) + Spring Integration MQTT:** Connects at startup (QoS 1). Sole inbound callback: `MqttInboundRouter` → handlers (`site/+/line/+/telemetry`, `site/+/line/+/commands/acks`); `MqttPublisher` for validated writes. Not the MQTT v5 client artifact.

  * **Spring WebMVC `SseEmitter` (or WebSockets):** Pushes real-time tag changes, alarm transitions, and command ACK updates to Next.js clients.

### 5.2 Plant Edge Service (`plant-edge-service`)

* **Language & Framework:** Java 21 + Spring Boot (leveraging **Eclipse Milo** for OPC UA and **Apache PLC4X** for S7/Modbus TCP) *or* Go for ultra-lightweight edge footprints. *(Note: Standardizing on Java 21 + Spring Boot for both Central and Edge allows sharing DTOs, validation logic, and unified team tooling).*

* **M2.1 OPC UA Probe (done):** Non-web Boot app; Milo `1.1.8`; endpoint `${OPC_UA_ENDPOINT:${OPC_ENDPOINT_URL:opc.tcp://localhost:53530/OPCUA/SimulationServer}}`; discovery+`EndpointUtil.updateUrl` host/port rewrite; SecurityPolicy None + Anonymous; 5s timeout; seven seed tags (INDORE LINE-01×3, INDORE LINE-02 VALVE_201_POS, PUNE LINE-01×3); initial READ then CHANGE subscription; log `OPC event=READ|CHANGE siteId=… lineId=… tagCode=… nodeId=… value=… quality=… sourceTimestamp=… serverTimestamp=… [status=…]`; fail-fast empty/invalid NodeId/connect/Bad read/Bad monitored-item. Mid-run loss reconnect is M2.2.

* **M2.2 Deadband + Disk Buffer (done):** `DeadbandFilter` keyed `siteId|lineId|tagCode`; per-tag `deadband` in yaml (valves 0.5, TEMP_C 0.2, TEMP_SP 0.1); same-kind numeric compare via `BigDecimal.valueOf`; bool/string COV; NaN/Inf never become lastGood; BAD repeat held; UNCERTAIN deadbands vs last uncertain emit; GOOD return uses lastGood baseline; only GOOD finite updates lastGood. `StoreAndForwardQueue` SQLite WAL (`app.buffer.path`, max 10000, drop-oldest + warn); publish outside lock; `inflightSeq`-safe eviction; `TelemetryLink` port. Mid-run OPC UA loss → generation-guarded backoff reconnect (`reconnect-initial-ms`/`max-ms`); filter+queue survive; startup fail-fast unchanged.

* **M2.3 MQTT Telemetry Publisher (done):** Paho `1.2.5` `MqttTelemetryLink` → `site/{siteId}/line/{lineId}/telemetry` QoS 1; Section 7.1 envelope + reading `nodeId`; memory persistence + `cleanSession`; delete SQLite row only after sync publish; `connectComplete`/`bindRecovery` → `onLinkRecovered`. `PacedOutboundDrainer` (`edge-buffer-drain`, `drain-batch-size`/`drain-pause-ms`); fast-path claims head seq under lock (no race with worker); open logs `residualDepth`; local `EdgeLinkHealth` (`CONNECTED`|`DISCONNECTED`, `bufferedCount`, `lastPublishAt`). Broker-down at startup does not fail OPC UA. Tests `EDGE-MQTT` / `EDGE-BUFFER`.

* **M2.4 Command Subscriber + OPC Write + ACK (done):** Second Paho client `${EDGE_MQTT_CLIENT_ID:plant-edge}-commands` (memory + `cleanSession=true` — no broker offline queue; re-subscribe `site/+/line/+/commands` on connect/`connectComplete`); single `edge-command` worker + capacity-32 queue; yaml allow-list resolve (`externalAddress`/`nodeId`/tagCode); `CommandWriter`/`OpcUaCommandWriter` via live `MiloOpcUaSession` write+readback; strict DataType coerce (Float `ns=0;i=10` / Double `ns=0;i=11` exact); ACK → `site/{siteId}/line/{lineId}/commands/acks` with dual aliases `appliedValue`/`observedValue` + `errorMessage`/`reason` (explicit JSON nulls); edge status `REJECTED` → central `FAILED_EDGE`; terminal ACK replay vs transient retry (`queue full` / session unavailable); `EdgeCommandHistory` cap 32. Prosys seed `ns=3;i=1001..1004` currently simulation generators (`Bad_NotWritable`; `1001` Int32, `1002–1004` Double) — live ACKED needs writable mapped nodes. Tests `EDGE-CMD`.

* **Local Resilience:** SQLite WAL outbound queue buffers filtered `TelemetryRecord`s when `TelemetryLink` is down; paced FIFO drain on recover; reopen logs residual depth and replays undeleted rows.

* **M2.5 Edge Device UI (done):** Servlet on `127.0.0.1:8081` (Tomcat max 8; central stays `8080`); `GET /api/edge/status` aggregates `OpcUaLinkHealth` + `TagObservationStore` (record every reading w/ `emitted` incl. deadband holds) + `MqttTelemetryLink.health()` + cmd `isConnected()` (cleared on `connectionLost`, not `messageArrived`) + `EdgeCommandHistory` (32); static poll page keeps last-good render on failed poll. Tests `EDGE-UI`.

### 5.3 Multi-Site Web Frontend (`web-frontend`)

* **Framework:** Next.js App Router, TypeScript, Tailwind v4, shadcn/ui (dark-slate SCADA shell).

* **Communication:** Native `EventSource` (SSE) for real-time telemetry/alarm/ACK streams; standard `fetch` REST calls for historical charts, tag configuration, and governed write requests.

* **M1.7/M1.8 Live Console:** Query-string site/line (`?siteId=&lineId=`, default `INDORE`/`LINE-01`); generation-scoped REST snapshot (`/api/telemetry/latest` + `/alarms`) plus same-origin fetch SSE (`/api/stream/telemetry` → backend; `TAG_UPDATE`/`ALARM_EVENT`/`COMMAND_UPDATE`); full `TagMapping` join for write affordances; connection pill includes `at-capacity`. Rewrites: `/api/telemetry/*`, `/api/registry/*`, `/api/commands` (+ path).

## 6. Data Models & Database Schema

### 6.1 Core Relational Tables (PostgreSQL)

1. **`sites`**:

   * `id` (UUID / String PK, e.g., `INDORE`, `PUNE`)

   * `name` (VARCHAR)

   * `timezone` (VARCHAR)

   * `status` (ENUM: `ONLINE`, `DEGRADED`, `OFFLINE`)

2. **`lines`**:

   * PK `(site_id, id)` — `id` e.g. `LINE-01` (Java property `lineId`); same line code allowed per site

   * `site_id` (FK -> `sites.id`)

   * `name` (VARCHAR)

3. **`tag_mappings`** (Flyway `V1__registry.sql` + `V2__tag_alarm_thresholds.sql`; unique `(site_id, line_id, tag_code)`):

   * `id` (UUID PK)

   * `site_id` (FK -> `sites.id`)

   * `line_id` (with `site_id` FK -> `lines(site_id, id)`)

   * `tag_code` (VARCHAR, unique per site+line, e.g., `VALVE_101_POS`, `REACTOR_TEMP_C`)

   * `description` (VARCHAR)

   * `protocol` (ENUM: `OPC_UA`, `S7`, `MODBUS_TCP`, `BACNET_IP`)

   * `endpoint_url` (VARCHAR; default seed `opc.tcp://localhost:53530/OPCUA/SimulationServer`, override `OPC_ENDPOINT_URL`)

   * `external_address` (VARCHAR, e.g., `ns=3;i=1001` or `DB1.DBD0`)

   * `data_type` (ENUM: `FLOAT`, `INT`, `BOOLEAN`, `STRING`)

   * `unit` (VARCHAR, e.g., `%`, `°C`, `bar`)

   * `writable` (BOOLEAN, default `false`)

   * `min_value` (DOUBLE PRECISION, nullable — enforced on writes)

   * `max_value` (DOUBLE PRECISION, nullable — enforced on writes)

   * `deadband` (DOUBLE PRECISION, default `0.0` — minimum delta required to publish telemetry)

   * `warning_low` / `warning_high` (DOUBLE PRECISION, nullable)

   * `critical_low` / `critical_high` (DOUBLE PRECISION, nullable; V2 migrated from `alarm_low`/`alarm_high`)

   * `interlock_rule_key` (VARCHAR, nullable — references pluggable backend safety rule)

   * REST: `GET /api/registry/tags?siteId&lineId&protocol&writable`; `POST /api/registry/tags` (201); `PUT /api/registry/tags/{id}` (siteId/lineId immutable); `DELETE /api/registry/tags/{id}` (204, missing → `TAG_NOT_FOUND`, evicts latest-cache; `telemetry_history` + `command_audit_logs` kept); errors `{code,message,fieldErrors}`

4. **`command_audit_logs` (Immutable Append-Only)**:

   * `command_id` (UUID PK)

   * `site_id` (VARCHAR)

   * `line_id` (VARCHAR)

   * `tag_code` (VARCHAR)

   * `requested_value` (VARCHAR)

   * `previous_value` (VARCHAR, nullable)

   * `requested_by` (VARCHAR — user ID/email)

   * `reason` (TEXT — mandatory operator note for digital logbook)

   * `status` (ENUM: `VALIDATED_DISPATCHED`, `REJECTED_VALIDATION`, `REJECTED_INTERLOCK`, `ACKED`, `FAILED_EDGE`, `TIMEOUT`)

   * `rejection_reason` (TEXT, nullable)

   * `applied_value` (VARCHAR, nullable — from edge ACK)

   * `created_at` (TIMESTAMPTZ)

   * `acked_at` (TIMESTAMPTZ, nullable)

   * Flyway `V4__command_audit_logs.sql`; identity columns insert-only (trigger); ACK/publish-failure transition only via `UPDATE ... WHERE status='VALIDATED_DISPATCHED'` → `ACKED`|`FAILED_EDGE`; SSE `COMMAND_UPDATE` only when update count=1. Edge ACK status `REJECTED` stored as `FAILED_EDGE`. MQTT publish failure → same conditional `FAILED_EDGE` + `rejection_reason` `MQTT publish failed: …` + SSE when transitioned. REST: `POST /api/commands` (202 / 422 / 503); `GET /api/commands/audit`; no update/delete endpoints. RBAC deferred (`requestedBy` caller-supplied).

### 6.2 Time-Series Hypertable (TimescaleDB)

5. **`telemetry_history`** (Flyway `V3__telemetry_history.sql`; Timescale hypertable on `time`):

   * `time` (TIMESTAMPTZ NOT NULL — partitioning column)

   * `site_id` (VARCHAR NOT NULL)

   * `line_id` (VARCHAR NOT NULL)

   * `tag_code` (VARCHAR NOT NULL)

   * `numeric_value` (DOUBLE PRECISION, nullable)

   * `boolean_value` (BOOLEAN, nullable)

   * `string_value` (VARCHAR, nullable)

   * `quality` (VARCHAR — e.g., `GOOD`, `BAD`, `UNCERTAIN`)

   * *Index:* `(site_id, line_id, tag_code, time DESC)`; lookup index `tag_mappings (site_id, line_id, external_address)`

   * Ingest: MQTT `site/+/line/+/telemetry` + `POST /api/telemetry/ingest` (batch envelope or single reading; resolve by `tagCode` or `externalAddress`). JDBC batch writer (cap 2000, flush 200/500ms). Cache `app.cache.mode=memory|redis`. Alarms: inclusive warning/critical; `BAD`/`UNCERTAIN` freeze severity. Query: `GET /api/telemetry/latest`, `/history` (`[from,to)` UTC), `/alarms`.
   * SSE (M1.5/M1.6): `GET /api/stream/telemetry?siteId=&lineId=` (`SseEmitter`); events `TAG_UPDATE` / `ALARM_EVENT` (`LatestTagStateResponse`) / `COMMAND_UPDATE` (`status` String; constants `VALIDATED_DISPATCHED|REJECTED_VALIDATION|REJECTED_INTERLOCK|ACKED|FAILED_EDGE|TIMEOUT`). Snapshot on connect = latest tags + active alarms (`severity != NONE`). Live `ALARM_EVENT` on severity/direction/frozen transition only. Clients: latest-wins per tag (not history). Ingest publishes via non-blocking `TelemetryStreamSink` → bounded dispatch (never `send` on ingest thread). Timeout: single `app.stream.emitter-timeout-ms` (also MVC async). Cap 32 → `503 STREAM_AT_CAPACITY`. Commands emit `COMMAND_UPDATE` after audit insert/conditional transition commits.

## 7. MQTT Topic Hierarchy & JSON Contracts

All edge-to-cloud and cloud-to-edge asynchronous communication uses MQTT QoS 1.

### 7.1 Telemetry Stream (Edge $\rightarrow$ Central)

* **Topic:** `site/{siteId}/line/{lineId}/telemetry`

* **Payload:**

```
{
  "messageId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "siteId": "INDORE",
  "lineId": "LINE-01",
  "timestamp": "2026-11-19T12:00:01.250Z",
  "readings": [
    {
      "tagCode": "VALVE_101_POS",
      "value": 75.5,
      "dataType": "FLOAT",
      "quality": "GOOD",
      "sourceTimestamp": "2026-11-19T12:00:01.200Z"
    }
  ]
}

```

### 7.2 Governed Command Dispatch (Central $\rightarrow$ Edge)

* **Topic:** `site/{siteId}/line/{lineId}/commands`

* **Payload:**

```
{
  "commandId": "c4a760a8-dbcf-4e14-9f39-645a8e937f12",
  "siteId": "INDORE",
  "lineId": "LINE-01",
  "tagCode": "VALVE_101_POS",
  "protocol": "OPC_UA",
  "externalAddress": "ns=3;i=1001",
  "dataType": "FLOAT",
  "targetValue": 50.0,
  "issuedBy": "operator@factory.com",
  "issuedAt": "2026-11-19T12:05:00.000Z"
}

```

### 7.3 Command Acknowledgement (Edge $\rightarrow$ Central)

* **Topic:** `site/{siteId}/line/{lineId}/commands/acks`

* **Payload:**

```
{
  "commandId": "c4a760a8-dbcf-4e14-9f39-645a8e937f12",
  "siteId": "INDORE",
  "lineId": "LINE-01",
  "tagCode": "VALVE_101_POS",
  "status": "ACKED",
  "appliedValue": 50.0,
  "errorMessage": null,
  "executedAt": "2026-11-19T12:05:00.145Z"
}

```

### 7.4 Edge Heartbeat & Health (Edge $\rightarrow$ Central)

* **Topic:** `site/{siteId}/edge/status`

* **Payload:**

```
{
  "siteId": "INDORE",
  "timestamp": "2026-11-19T12:05:10.000Z",
  "plcConnected": true,
  "activeProtocol": "OPC_UA",
  "endpointUrl": "opc.tcp://localhost:53530/OPCUA/SimulationServer",
  "bufferedMessagesCount": 0
}

```

## 8. Governed Write Command Lifecycle

When an operator submits a setpoint/valve change in Next.js:

1. **UI Confirmation:** Next.js displays a modal showing current value, allowed `[minValue, maxValue]` bounds, and requires a digital logbook `reason`.

2. **API Request:** `POST /api/commands` sent to Central Spring Boot.

3. **Synchronous Backend Guardrails:**

   * Verify user RBAC permissions (`OPERATOR` or `SUPERVISOR`).

   * Verify `tag.writable == true`.

   * Verify `minValue <= targetValue <= maxValue`.

   * Evaluate pluggable `InterlockRule` / `InterlockEvaluator` (e.g., `INLET_VALVE_OPEN` requires same-line `VALVE_101_POS` GOOD finite value ≥ 5 using latest cached tag values). RBAC not enforced until an identity module exists.

   * If any check fails: Persist `REJECTED_VALIDATION` or `REJECTED_INTERLOCK` row in `command_audit_logs` and return HTTP `422 Unprocessable Entity` with clear details.

4. **Dispatch & Audit:** If valid, persist `VALIDATED_DISPATCHED` in `command_audit_logs`, publish to MQTT `site/{siteId}/line/{lineId}/commands`, emit SSE event `COMMAND_UPDATE`, and return HTTP `202 Accepted` with `commandId`.

5. **Edge Execution:** Plant Edge Service receives the MQTT message, writes to the PLC/OPC UA node, verifies the write, and publishes `ACKED` (or `FAILED_EDGE`) to `site/{siteId}/line/{lineId}/commands/acks`.

6. **Completion:** Central Backend consumes the ACK, conditionally updates `command_audit_logs` to `ACKED` or `FAILED_EDGE`, and pushes that final status to Next.js via SSE only when the row actually transitions.

## 9. Local Simulation & Development Environment

Operator dry-run + config cutover: [`DEMO_AND_CONFIG_PLAYBOOK.md`](../DEMO_AND_CONFIG_PLAYBOOK.md) (+ `scripts/Prepare-Demo.ps1`).

Physical PLC hardware is not required for development or QA:

* **OPC UA Simulator:** **Prosys OPC UA Simulation Server** (Free Edition) running locally at `opc.tcp://localhost:53530/OPCUA/SimulationServer` (or `opc.tcp://Tanmay:53530/OPCUA/SimulationServer`).

* **Modbus TCP Simulator:** `ModbusPal` or `Diagslave` on `127.0.0.1:502`.

* **Siemens S7 Simulator:** `Snap7 Server` on TCP port `102` (or Siemens PLCSIM Advanced).

* **Full SCADA Simulator:** Inductive Automation **Ignition Maker Edition** (free up to 10,000 tags).

## 10. Implementation Roadmap & Milestones

Follow this exact sequence when scaffolding and implementing features:

### Milestone 1: Central Control Plane, Persistence & Next.js UI

* **Step M1.1 — Local Infrastructure (`docker-compose.yml`):** Spin up TimescaleDB (`timescale/timescaledb:latest-pg16`), Redis, and Eclipse Mosquitto MQTT broker. Verify DB connection and `mosquitto_pub`/`mosquitto_sub`.

* **Step M1.2 — Spring Boot Skeleton & Seed Data (done):** `central-backend` Maven/Java 21/Boot 3.5; Flyway V1 `sites`/`lines`/`tag_mappings`; idempotent seed (`INDORE`/`PUNE`, 7 tags); `GET /api/registry/tags?siteId=&lineId=` (optional filters). OPC URL via `OPC_ENDPOINT_URL`.

* **Step M1.3 — Tag Registry CRUD API (done):** `POST`/`PUT`/`GET`/`DELETE` for `TagMapping` with filters (`siteId`, `lineId`, `protocol`, `writable`); bounds + warning/critical thresholds; bean validation and stable error codes. `DELETE /api/registry/tags/{id}` → 204; unknown id `TAG_NOT_FOUND`; drops that tag’s latest-cache/alarm entry; history and audit rows stay.

* **Step M1.4 — Telemetry Ingestion, Latest-State Cache & TimescaleDB (done):** MQTT `site/+/line/+/telemetry` + `POST /api/telemetry/ingest`; latest-tag cache (`memory` default / `redis`); threshold alarms + BAD freeze; batch JDBC into `telemetry_history`; `GET /api/telemetry/latest|history|alarms`.

* **Step M1.5 — Live SSE Stream (done):** `GET /api/stream/telemetry` (`SseEmitter`); `TAG_UPDATE` / `ALARM_EVENT` / `COMMAND_UPDATE`; optional `siteId`/`lineId`; non-blocking dispatch; `app.stream.emitter-timeout-ms`; `publishCommand` hook for M1.6.

* **Step M1.6 — Governed Command Engine & Audit Log (done):** `POST /api/commands` (writable, min/max, finite targets, pluggable `InterlockRule`/`InterlockEvaluator`, immutable `command_audit_logs`, MQTT `commands` QoS 1); ACK listener `site/+/line/+/commands/acks` via shared `MqttInboundRouter`; conditional `VALIDATED_DISPATCHED`→`ACKED`|`FAILED_EDGE`; `GET /api/commands/audit`; SSE `COMMAND_UPDATE` on real transitions only.

* **Step M1.7 — Next.js Shell & Live Console (done):** `web-frontend` App Router shell; Site/Line switcher (INDORE/PUNE); generation-scoped SSE + REST snapshot; telemetry table + active alarm banner; control-plane connection health pill.

* **Step M1.8 — Next.js Write Modal, Audit Logbook & Registry UI (done):** `WriteCommandModal` (frozen snapshot + live drift line, typed target readiness incl. `0`/`false`/`""`, confirm gate, `POST /api/commands`, structured `code`/`fieldErrors`); `/audit` filterable logbook (`page` reset on filter change; SSE patches visible `commandId` only); `/registry` create/edit/delete (`IDENTITY_IMMUTABLE` site/line; client+server bounds; delete confirms then `DELETE`, history/audit kept). Shared fetch SSE session (`connectTelemetryStream`: one connection, capacity backoff, pause while hidden; `at-capacity` pill). Next rewrite `/api/commands` (+ `/api/commands/:path*`). Nav Live/Audit/Registry keeps `?siteId=&lineId=`.

### Milestone 2: Plant Edge Service, Edge UI & Live PLC/OPC UA Loop

* **Step M2.1 — OPC UA Connectivity Probe (done):** `plant-edge-service` Eclipse Milo 1.1.8; connect/read/subscribe Prosys Simulation nodes matching M1 seed (7 tags); structured console READ/CHANGE logs; endpoint rewrite + fail-fast; no MQTT yet.

* **Step M2.2 — Per-Tag Deadband Filter & Persistent Store-and-Forward Buffer (done):** Per-tag deadband/COV (`DeadbandFilter`); SQLite WAL `StoreAndForwardQueue` + `TelemetryLink`; OPC UA mid-run loss → backoff reconnect (filter/queue preserved); tests `EDGE-DEADBAND` / `EDGE-BUFFER` / reconnect.

* **Step M2.3 — MQTT Telemetry Publisher (done):** Paho publisher to `site/{siteId}/line/{lineId}/telemetry` (Section 7.1 + `nodeId`); paced drain; residual backlog log; local link health; E2E into Spring Boot ingest + Next.js live console.

* **Step M2.4 — Edge Command Subscriber, PLC Write & ACK Publisher (done):** Subscribe to `site/{siteId}/line/{lineId}/commands`, execute typed writes against the OPC UA server (with Apache PLC4X abstraction ready for S7/Modbus TCP), and publish `ACKED` or `REJECTED` (central stores as `FAILED_EDGE`) to `site/{siteId}/line/{lineId}/commands/acks`.

* **Step M2.5 — Lightweight Edge Device UI (done):** Local status/diagnostics on `127.0.0.1:8081` (yaml `server.address`; container overrides `SERVER_ADDRESS=0.0.0.0` and publishes `127.0.0.1:8081` on the plant PC); `GET /api/edge/status` + static poll UI for OPC UA health, MQTT/buffer, tag deadbands + last observed (holds marked), recent commands.

* **Step M2.6 — End-to-End Verification & Playbook:** Run full loop rehearsal: Next.js write command $\rightarrow$ Spring Boot validation/interlock check $\rightarrow$ MQTT $\rightarrow$ Edge Service $\rightarrow$ Prosys OPC UA Server write $\rightarrow$ ACK $\rightarrow$ Next.js UI & Digital Audit Logbook.
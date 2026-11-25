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
| `docker-compose.edge.yml` | plant PC | `plant-edge-service` only. `SERVER_ADDRESS=0.0.0.0` inside the container; host publishes `127.0.0.1:8081` so diagnostics stay local. Volume `/data` holds both the SQLite WAL (`EDGE_BUFFER_PATH`) and the config snapshot (`EDGE_CONFIG_SNAPSHOT_PATH`). `MQTT_BROKER_URL` default `tcp://host.docker.internal:1883`; `CENTRAL_BASE_URL` default `http://host.docker.internal:8080` (config sync is HTTP, not MQTT). `EDGE_SITE_ID` default `INDORE`, case-sensitive. OPC default `opc.tcp://host.docker.internal:53530/OPCUA/SimulationServer` (`OPC_UA_ENDPOINT`, else `OPC_ENDPOINT_URL`). Heap `-Xmx384m`. |
| `docker-compose.dev.yml` | dev laptop | Cloud unit + edge on one compose network (`tcp://mosquitto:1883`, `CENTRAL_BASE_URL=http://central-backend:8080`). Prosys stays on the Windows host. |

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

* **M2.1 OPC UA Probe (done):** Non-web Boot app; Milo `1.1.8`; endpoint `${OPC_UA_ENDPOINT:${OPC_ENDPOINT_URL:opc.tcp://localhost:53530/OPCUA/SimulationServer}}`; discovery+`EndpointUtil.updateUrl` host/port rewrite; `app.opcua.security.*` selects endpoint (`None`+`anonymous` local/dev; `Basic256Sha256` SignAndEncrypt + `pki-dir` for secured servers — missing trust = `IllegalStateException` fail-fast, not BACKOFF); 5s timeout; seven seed tags use `nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=…` (INDORE LINE-01×3, INDORE LINE-02 VALVE_201_POS, PUNE LINE-01×3); `MiloOpcUaSession.start` syntax-checks addresses, connects, reads `Server_NamespaceArray`, resolves URI→index (no non-zero `ns=` in YAML/seeds); initial READ then CHANGE subscription; log `OPC event=READ|CHANGE … quality=…`; `QualityGate` for trust; fail-fast empty/invalid NodeId/Bad initial read/Bad monitored-item; unreachable server (`OpcUaUnavailableException`) soft-starts into BACKOFF. Tests `EDGE-NS` / `EDGE-QUALITY` / `EDGE-TYPE` / `EDGE-RECONNECT` / `EDGE-SEC`. Mid-run loss reconnect is M2.2.

* **M2.2 Deadband + Disk Buffer (done):** `DeadbandFilter` keyed `siteId|lineId|tagCode`; per-tag `deadband` in yaml (valves 0.5, TEMP_C 0.2, TEMP_SP 0.1); same-kind numeric compare via `BigDecimal.valueOf`; bool/string COV; NaN/Inf never become lastGood; BAD repeat held; UNCERTAIN deadbands vs last uncertain emit; GOOD return uses lastGood baseline; only `QualityGate.requireGood` + finite updates lastGood. `StoreAndForwardQueue` SQLite WAL (`app.buffer.path`, max 10000, drop-oldest + warn); publish outside lock; `inflightSeq`-safe eviction; `TelemetryLink` port. Mid-run OPC UA loss → `session.close` then `start` (namespace re-resolve + subscription recreate); generation-guarded backoff (`reconnect-initial-ms`/`max-ms`); filter+queue survive; startup unreachable server uses the same BACKOFF path (config/PKI errors still fail-fast).

* **M2.3 MQTT Telemetry Publisher (done):** Paho `1.2.5` `MqttTelemetryLink` → `site/{siteId}/line/{lineId}/telemetry` QoS 1; Section 7.1 envelope + reading `nodeId`; memory persistence + `cleanSession`; delete SQLite row only after sync publish; `connectComplete`/`bindRecovery` → `onLinkRecovered`. `PacedOutboundDrainer` (`edge-buffer-drain`, `drain-batch-size`/`drain-pause-ms`); fast-path claims head seq under lock (no race with worker); open logs `residualDepth`; local `EdgeLinkHealth` (`CONNECTED`|`DISCONNECTED`, `bufferedCount`, `lastPublishAt`). Broker-down at startup does not fail OPC UA. Tests `EDGE-MQTT` / `EDGE-BUFFER`.

* **M2.4 Command Subscriber + OPC Write + ACK (done):** Second Paho client `${EDGE_MQTT_CLIENT_ID:plant-edge}-commands` (memory + `cleanSession=true` — no broker offline queue; re-subscribe `site/+/line/+/commands` on connect/`connectComplete`); single `edge-command` worker + capacity-32 queue; yaml allow-list resolve (`externalAddress`/`nodeId`/tagCode` as `nsu=` strings); `CommandWriter`/`OpcUaCommandWriter` via live `MiloOpcUaSession.resolve` + write+readback; `QualityGate` on DataType attr / write status / readback (Uncertain ≠ ACK); `CommandValueCoercer` only Variant builder (Float→`float` finite, UInt16→`UShort` 0..65535); ACK → `site/{siteId}/line/{lineId}/commands/acks` with dual aliases `appliedValue`/`observedValue` + `errorMessage`/`reason` (explicit JSON nulls); edge status `REJECTED` → central `FAILED_EDGE`; terminal ACK replay vs transient retry (`queue full` / session unavailable); `EdgeCommandHistory` cap 32. Writable seed tags share Prosys Constant Double `nsu=…;i=1007` (Counter `i=1001` Int32 read-only, `i=1002`–`i=1006` read-only generators). `REACTOR_TEMP_C` stays Random `nsu=…;i=1002`. Telemetry may include `nodeWritable`. Latest cache stores `observedDataType` + `nodeWritable`. `POST /api/commands` rejects `NODE_TYPE_MISMATCH` / `NODE_NOT_WRITABLE` (422, `REJECTED_VALIDATION`) when those facts are already known. Tests `EDGE-CMD` / `EDGE-TYPE` / `GW-NODE`.

* **Local Resilience:** SQLite WAL outbound queue buffers filtered `TelemetryRecord`s when `TelemetryLink` is down; paced FIFO drain on recover; reopen logs residual depth and replays undeleted rows.

* **M3.3 Edge Onboarding Probe (done):** Edge dials; Central only forwards. Edge `POST /api/onboarding/probe` on loopback `:8081` (same trust as `GET /api/edge/status`). Central registers `AccessAction.ONBOARDING_PROBE` (admin only), same gates, then posts the validated `byte[]` to `${EDGE_BASE_URL:http://127.0.0.1:8081}` (one local edge for this onboarding path, not multi-site routing; 15s RestClient; copy 200/409/422 only when `Content-Type` is `application/json`, else 502 `EDGE_BAD_RESPONSE`; 503 `EDGE_UNREACHABLE` only when no HTTP response). Both controllers take `HttpServletRequest` (no `@RequestBody`). Gate order (later gates do not run): auth on Central only (401/403 `FORBIDDEN`) → flag `app.onboarding.probe-enabled` (default **false**; 403 `PROBE_DISABLED`, stream unread) → 413 `BODY_TOO_LARGE` (32 KiB) → 400 `{"code":"INVALID_REQUEST_BODY","message":"probe body is invalid"}` from a probe-only `ObjectMapper.copy()` whose `CoercionConfig` fails Integer/Float/Boolean into `LogicalType.Textual` (not an `ObjectMapper` bean); root JSON `null` is the same 400 (`readValue` returns Java null and does not throw) → 422 `ENDPOINT_URL_INVALID`|`SECURITY_MODE_UNSUPPORTED`|`NODE_IDS_LIMIT_EXCEEDED`|`NODE_ID_TOO_LONG` (full `fieldErrors` in that order; `securityMode` exact `None`|`SignAndEncrypt`; `Sign` waits for M4.3) → edge `tryLock` (409) / Central forward of the same `byte[]`. Raw-authority URL parse (opaque/empty authority/userinfo/query/fragment rejected, bare "?" and "#" included, %3F and %23 kept on the path; `_` hosts kept; bracket interior `[0-9A-Fa-f:.]+`; host ≤253; bracket remainder after `]` is empty or `:` + port (`[::1]4840` rejected); port `[0-9]{1,5}` no leading zero then `1..65535`; omitted port 4840; dial URL `opc.tcp://host:port` + `rawPath` or `""`). Edge: 8s deadline after `tryLock`; SignAndEncrypt `pki-dir` before DNS; resolve on fixed 2-thread `opcua-probe-io` (`SynchronousQueue`/abort → `probe I/O pool saturated`); bounded `Socket.connect` on the probe thread before `DiscoveryClient.getEndpoints(canonical).get()`+rewrite on that same pool; `createForProbe` (request mode, Anonymous); one value `readValues` + one DataType `read`; `disconnect().get(2s)` only (no `Stack.releaseSharedResources()`). Envelope qualities `GOOD`|`BAD`|`UNREACHABLE`; empty `nodeIds` is M3.5 Step 2 (`error` null ⇒ namespace table read); M3.5 Step 3 chunks at 32. Shared fixture `contracts/onboarding-probe-validation.json`. Tests `EDGE-PROBE`; sign-off tag `EDGE-PROBE-M33` (combined Prosys `i=1001`/`i=1002`/`i=999999`; Assumptions-skip when 53530 is down does not close the milestone).

* **M3.4 Registry→Edge Config Sync + OPC Hot-Reload (done):** One site per edge process (`app.edge.site-id` / `EDGE_SITE_ID`, `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`; `getNormalizedSiteId()` trims on read). `app.edge.config-sync-enabled` defaults **true** and `app.opcua.tags` is **gone from the main yaml** — central is the single source. Static fallback = `EDGE_CONFIG_SYNC_ENABLED=false` **plus** an explicit tag list (test fixture `application-static-tags.yml`, profile `static-tags`, used by `SeedAlignmentTest`); with sync on and no tags the probe no longer fails fast, it idles. `EdgeConfigSync` (`edge-config-sync` daemon, `ApplicationReadyEvent`, `config-poll-ms` 15s / `config-timeout-ms` 5s) does conditional `GET /api/edge/{siteId}/config` (`If-None-Match: "v{version}"`) and owns `EdgeSyncView` only (`NONE`|`CENTRAL`|`LOCAL_SNAPSHOT`|`STATIC_YAML`); bootstrap reconciles the last-known-good snapshot first, then polls. `EdgeConfigSnapshotStore` = atomic `.tmp` + `ATOMIC_MOVE` envelope `{schemaVersion,siteId,savedAt,sha256,manifestJson}`; checksum/parse failure → `.bad` quarantine + treated absent; foreign `siteId` ignored in place. Accepted manifest bodies need matching `siteId`, `version >= 1`, non-blank `contentHash`, array `lines`. `EdgeConfigClient` needs `@Autowired` on its single-arg ctor (the RestClient ctor is test-only).
  * **Reconcile (all on the single `opcua-reconnect` thread via `EdgeConfigReconcileHook` → `OpcUaProbe.requestReconcile` → `OpcUaReconciler`):** `DesiredTagClassifier` runs first with no I/O — rejects `unsupported protocol` / `missing siteId/lineId/tagCode` / `deadband must be >= 0` / `invalid node id syntax` / `duplicate site/line/tagCode`. Phase branches: **empty accepted** → clear catalog, empty `ConfiguredTagIndex`, clear store + filter, `activeTagCount=0`; UP → intentional close → IDLE, BACKOFF → cancel timer → IDLE (never schedules). **non-empty + BACKOFF** → no-op (the scheduled attempt owns it). **non-empty + IDLE** → `session.startLenient`; `OpcUaUnavailableException` → BACKOFF + schedule (delay doubles only on a reconnect pass). **non-empty + UP** → identity diff on tagKey→`externalAddress`: unchanged ⇒ deadband/writable only, no subscription work (baselines survive); changed ⇒ `prepareSwap` (one NamespaceArray refresh per pass, initial READ only for new/re-addressed keys, buffered) → empty accepted ⇒ `abortSwap` + IDLE path → else `deactivateReadings` (old listeners go no-op through a token gate) → publish catalog → `prune`/`evict` (DeadbandFilter UNSEEDED for new/address-changed/removed) → `commitSwap` (flush buffered READs → `setPublishingMode(true)` → delete old) → replace `ConfiguredTagIndex` + `EdgeApplyView`. `contentHash` short-circuit only when phase is UP **and** `appliedRejectedCount == 0`. `onSessionLost`: intentional close or empty desired → IDLE with no backoff.
  * **Lenient OPC UA:** `MiloOpcUaSession.startLenient` / `prepareSwap` / `deactivateReadings` / `commitSwap` / `abortSwap` + `OpcUaApply.{Reject,Result}`. `NamespaceResolver` holds strict `parseAll`/`resolveAll` and lenient `resolveLenient` (`unknown namespace uri`); `LenientTags` holds per-tag `bad initial read` plus a parked subscription (`create()` → `setPublishingMode(false)` → `createMonitoredItems()` → per-item `bad monitored item create`). Strict `start()` (static mode) is unchanged and still fail-fast. Connection failure always throws; only per-tag faults become rejects.
  * **Phase `IDLE`** is first-class: `isRunning()` is `phase != STOPPED` so MQTT, commands, and diagnostics stay up while the edge is intentionally connected to nothing; `OpcUaLinkHealth.markIdle` → `DISCONNECTED`/`IDLE` with `lastError` cleared (M4.4 heartbeat must not flag it offline or stale).
  * **MQTT site scope:** `MqttCommandClient` takes the filter string; sync on ⇒ `site/{siteId}/line/+/commands` (the `site/+/...` wildcard survives only in static mode). `CommandDispatcher` holds an `AtomicReference<ConfiguredTagIndex>` (`replaceConfiguredTags`), silently drops cross-site topics with no ACK when sync is on, and rejects `tag is not writable`.
  * **Status/UI:** `GET /api/edge/status` gains `config` (`EdgeSyncView` + `EdgeApplyState`/`EdgeApplyView`: `syncEnabled`, `siteId`, `source`, fetched/applied version + hash, `lastSyncedAt`/`lastAttemptAt`/`lastError`/`lastAppliedAt`, `activeTagCount`, `rejectedCount`, `rejected[{lineId,tagCode,externalAddress,reason}]`); `tags[]` come from `ActiveTagCatalog` (LinkedHashMap = manifest order) when sync is on or the catalog is non-empty, else yaml, and now carry `writable`. `TagObservationStore` gates `record` on the catalog once published and `prune`s to allowed keys under a leaf lock. Static page adds the config card, a writable column, and a muted `IDLE` pill (no "server not live" banner while IDLE). Compose: `EDGE_CONFIG_SYNC_ENABLED`/`EDGE_SITE_ID`/`CENTRAL_BASE_URL`/`EDGE_CONFIG_SNAPSHOT_PATH=/data/edge-config.json` on `docker-compose.edge.yml`; dev overrides `CENTRAL_BASE_URL=http://central-backend:8080`. Tests `EDGE-CONFIG`.

* **M2.5 Edge Device UI (done):** Servlet on `127.0.0.1:8081` (Tomcat max 8; central stays `8080`); `GET /api/edge/status` aggregates `OpcUaLinkHealth` + `TagObservationStore` (record every reading w/ `emitted` incl. deadband holds) + `MqttTelemetryLink.health()` + cmd `isConnected()` (cleared on `connectionLost`, not `messageArrived`) + `EdgeCommandHistory` (32); static poll page keeps last-good render on failed poll. Tests `EDGE-UI`.

### 5.3 Multi-Site Web Frontend (`web-frontend`)

* **Framework:** Next.js App Router, TypeScript, Tailwind v4, shadcn/ui (dark-slate SCADA shell).

* **Communication:** Native `EventSource` (SSE) for real-time telemetry/alarm/ACK streams; standard `fetch` REST calls for historical charts, tag configuration, and governed write requests.

* **M1.7/M1.8 Live Console:** Query-string site/line (`?siteId=&lineId=`, default `INDORE`/`LINE-01`); generation-scoped REST snapshot (`/api/telemetry/latest` + `/alarms`) plus same-origin fetch SSE (`/api/stream/telemetry` → backend; `TAG_UPDATE`/`ALARM_EVENT`/`COMMAND_UPDATE`); full `TagMapping` join for write affordances; connection pill includes `at-capacity`. Rewrites: `/api/telemetry/*`, `/api/registry/*`, `/api/commands` (+ path).

## 6. Data Models & Database Schema

### 6.1 Core Relational Tables (PostgreSQL)

1. **`sites`** (Flyway V1 + `V5__registry_lifecycle.sql`):

   * `id` (String PK / `siteCode`, e.g., `INDORE`, `PUNE`)

   * `name` (VARCHAR)

   * `timezone` (VARCHAR)

   * `status` (ENUM: `ONLINE`, `DEGRADED`, `OFFLINE` — connectivity)

   * `lifecycle` (ENUM: `DRAFT`, `VERIFIED`, `ACTIVE`; V5 default `ACTIVE` for seed; API create default `DRAFT`)

   * REST: `GET/POST /api/registry/sites`; `GET/PUT /api/registry/sites/{siteCode}` (POST idempotent on `siteCode`: 201 create / 200 existing)

2. **`lines`**:

   * PK `(site_id, id)` — `id` / `lineCode` e.g. `LINE-01` (Java property `lineId`); same line code allowed per site

   * `site_id` (FK -> `sites.id`)

   * `name` (VARCHAR)

   * `lifecycle` (`DRAFT` / `VERIFIED` / `ACTIVE`; V5)

   * REST: `GET/POST /api/registry/lines`; `GET/PUT /api/registry/lines/{siteCode}/{lineCode}` (POST idempotent on `(siteCode,lineCode)`)

3. **`tag_mappings`** (Flyway `V1__registry.sql` + `V2__tag_alarm_thresholds.sql` + V5; unique `(site_id, line_id, tag_code)`):

   * `id` (UUID PK)

   * `site_id` (FK -> `sites.id`)

   * `line_id` (with `site_id` FK -> `lines(site_id, id)`)

   * `tag_code` (VARCHAR, unique per site+line, e.g., `VALVE_101_POS`, `REACTOR_TEMP_C`)

   * `description` (VARCHAR)

   * `protocol` (ENUM: `OPC_UA`, `S7`, `MODBUS_TCP`, `BACNET_IP`)

   * `endpoint_url` (VARCHAR; default seed `opc.tcp://localhost:53530/OPCUA/SimulationServer`, override `OPC_ENDPOINT_URL`)

   * `external_address` (VARCHAR, e.g., `nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007` or `DB1.DBD0`)

   * `data_type` (ENUM: `FLOAT`, `INT`, `BOOLEAN`, `STRING`)

   * `unit` (VARCHAR, e.g., `%`, `°C`, `bar`)

   * `writable` (BOOLEAN, default `false`)

   * `min_value` (DOUBLE PRECISION, nullable — enforced on writes)

   * `max_value` (DOUBLE PRECISION, nullable — enforced on writes)

   * `deadband` (DOUBLE PRECISION, default `0.0` — minimum delta required to publish telemetry)

   * `warning_low` / `warning_high` (DOUBLE PRECISION, nullable)

   * `critical_low` / `critical_high` (DOUBLE PRECISION, nullable; V2 migrated from `alarm_low`/`alarm_high`)

   * `interlock_rule_key` (VARCHAR, nullable — references pluggable backend safety rule)

   * `lifecycle` (`DRAFT` / `VERIFIED` / `ACTIVE`; V5). Live only when site+line+tag all `ACTIVE` (`RegistryLiveness` / `RegistryLivenessService` single gate: ingest, commands `TAG_NOT_ACTIVE`, latest/alarms/SSE `filterLive`). Site/line demotion uses `evictScope` (O(cached), no per-tag DB). Tag/batch demotion uses `reconcileCache`/`removeCacheEntry`. Batch may set `ACTIVE` when parents are live but never seeds latest-cache — next ingest sample fills it.

   * REST: `GET /api/registry/tags?siteId&lineId&protocol&writable`; `POST /api/registry/tags` (201, default lifecycle `ACTIVE`); `PUT /api/registry/tags/{id}` (siteId/lineId immutable); `DELETE /api/registry/tags/{id}` (204, missing → `TAG_NOT_FOUND`, evicts latest-cache; `telemetry_history` + `command_audit_logs` kept); `POST /api/registry/tags/batch` JSON/CSV upsert (200 row summary; batch default lifecycle `DRAFT`; strict `minValue < maxValue`); errors `{code,message,fieldErrors}`

4. **`command_audit_logs` (Immutable Append-Only)**:

   * `command_id` (UUID PK)

   * `site_id` (VARCHAR)

   * `line_id` (VARCHAR)

   * `tag_code` (VARCHAR)

   * `requested_value` (VARCHAR)

   * `previous_value` (VARCHAR, nullable)

   * `requested_by` (VARCHAR — server-derived actor id from `ActorContext`; body `requestedBy` ignored)

   * `reason` (TEXT — mandatory operator note for digital logbook)

   * `status` (ENUM: `VALIDATED_DISPATCHED`, `REJECTED_VALIDATION`, `REJECTED_INTERLOCK`, `ACKED`, `FAILED_EDGE`, `TIMEOUT`)

   * `rejection_reason` (TEXT, nullable)

   * `applied_value` (VARCHAR, nullable — from edge ACK)

   * `created_at` (TIMESTAMPTZ)

   * `acked_at` (TIMESTAMPTZ, nullable)

   * Flyway `V4__command_audit_logs.sql`; identity columns insert-only (trigger); ACK/publish-failure transition only via `UPDATE ... WHERE status='VALIDATED_DISPATCHED'` → `ACKED`|`FAILED_EDGE`; SSE `COMMAND_UPDATE` only when update count=1. Edge ACK status `REJECTED` stored as `FAILED_EDGE`. MQTT publish failure → same conditional `FAILED_EDGE` + `rejection_reason` `MQTT publish failed: …` + SSE when transitioned. REST: `POST /api/commands` (202 / 422 / 503; gated `OPERATOR`|`SUPERVISOR`); `GET /api/commands/audit` open; no update/delete endpoints. MQTT `issuedBy` = same actor id as `requested_by`.

5. **`actors`** (Flyway `V6__actors.sql`): `actor_id` PK, `display_name`, `role` (`VIEWER`|`OPERATOR`|`SUPERVISOR` only). Bootstrap admin is env-only (`APP_BOOTSTRAP_TOKEN` / `X-Setup-Token` → `ONBOARDING_ADMIN`), not a row. REST: `GET`/`POST /api/access/actors` (admin only; create 201 / update 200; `ROLE_NOT_ASSIGNABLE` 422). `AccessInterceptor` + `AccessPolicy` single enforcement point; path normalized via `ServletRequestPathUtils`; bad setup token or unknown `X-Actor-Id` → 401 on open and gated routes; open `Anonymous`/`Resolved` do not set holder; unmatched paths skip resolver (`POST /api/onboarding/probe` → `ONBOARDING_PROBE`, admin only, since M3.3; `GET` on that path stays unmatched). `NEXT_PUBLIC_BOOTSTRAP_TOKEN` in browser bundle until M4.2.

### 6.2 Time-Series Hypertable (TimescaleDB)

6. **`telemetry_history`** (Flyway `V3__telemetry_history.sql`; Timescale hypertable on `time`):

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
      "nodeWritable": true,
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
  "externalAddress": "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007",
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

   * If the latest sample already has `observedDataType` or `nodeWritable`, reject `NODE_TYPE_MISMATCH` or `NODE_NOT_WRITABLE` before MQTT. Missing facts still fall through to the edge coercer.

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

* **Step M2.1 — OPC UA Connectivity Probe (done):** `plant-edge-service` Eclipse Milo 1.1.8; connect/read/subscribe Prosys Simulation nodes matching M1 seed (7 tags); structured console READ/CHANGE logs; endpoint rewrite; config fail-fast; unreachable server soft-starts; no MQTT yet.

* **Step M2.2 — Per-Tag Deadband Filter & Persistent Store-and-Forward Buffer (done):** Per-tag deadband/COV (`DeadbandFilter`); SQLite WAL `StoreAndForwardQueue` + `TelemetryLink`; OPC UA mid-run loss → backoff reconnect (filter/queue preserved); tests `EDGE-DEADBAND` / `EDGE-BUFFER` / reconnect.

* **Step M2.3 — MQTT Telemetry Publisher (done):** Paho publisher to `site/{siteId}/line/{lineId}/telemetry` (Section 7.1 + `nodeId`); paced drain; residual backlog log; local link health; E2E into Spring Boot ingest + Next.js live console.

* **Step M2.4 — Edge Command Subscriber, PLC Write & ACK Publisher (done):** Subscribe to `site/{siteId}/line/{lineId}/commands`, execute typed writes against the OPC UA server (with Apache PLC4X abstraction ready for S7/Modbus TCP), and publish `ACKED` or `REJECTED` (central stores as `FAILED_EDGE`) to `site/{siteId}/line/{lineId}/commands/acks`.

* **Step M2.5 — Lightweight Edge Device UI (done):** Local status/diagnostics on `127.0.0.1:8081` (yaml `server.address`; container overrides `SERVER_ADDRESS=0.0.0.0` and publishes `127.0.0.1:8081` on the plant PC); `GET /api/edge/status` + static poll UI for OPC UA health, MQTT/buffer, tag deadbands + last observed (holds marked), recent commands.

* **Step M2.6 — End-to-End Verification & Playbook:** Run full loop rehearsal: Next.js write command $\rightarrow$ Spring Boot validation/interlock check $\rightarrow$ MQTT $\rightarrow$ Edge Service $\rightarrow$ Prosys OPC UA Server write $\rightarrow$ ACK $\rightarrow$ Next.js UI & Digital Audit Logbook.

### Milestone 3: Self-Serve Plant Onboarding, Config Sync & Operator Access

* **Step M3.1 — Site, Line & Bulk Tag Registry APIs (done):** Remove the dependency on `RegistrySeedRunner` and manual code changes to add a new plant, production line, or equipment tag list (`RegistrySeedRunner` remains the INDORE/PUNE demo baseline only). Idempotent `POST`/`GET`/`PUT` for Sites and Lines on natural keys (`siteCode`, `lineCode`; retries never create duplicates). `POST /api/registry/tags/batch` JSON/CSV upsert with row-level validation (`minValue < maxValue`, `deadband >= 0`, valid protocol, non-empty `externalAddress`). Lifecycle `DRAFT` / `VERIFIED` / `ACTIVE` on sites, lines, and tags so partial config does not alarm or write; live only when all three are `ACTIVE` (`RegistryLivenessService` gate for ingest, `TAG_NOT_ACTIVE` commands, latest/alarms/SSE; site/line demotion `evictScope`; batch may set `ACTIVE` but never seeds latest-cache). `ActorContext` is a pass-through on mutating registry methods in M3.1 — bootstrap admin + RBAC in M3.2, Spring Security in M4.2. Test: create site + line via API; import a multi-tag reactor batch with one invalid row (clear row-level errors); re-import stays idempotent.

* **Step M3.2 — Actor Identity Abstraction & Bootstrap Admin Access (done):** `ActorContext` + `AccessInterceptor`/`AccessPolicy`/`BootstrapActorResolver`; `APP_BOOTSTRAP_TOKEN`/`X-Setup-Token` → `ONBOARDING_ADMIN`; `actors` directory + `X-Actor-Id`; registry mutations admin-only; commands `OPERATOR`|`SUPERVISOR`; `requested_by`/`issuedBy` from actor id (accept + reject); tests `SF-ACCESS`. M4.2 replaces `ActorResolver` only. M3.3 registered `POST /api/onboarding/probe` as `ONBOARDING_PROBE`.

* **Step M3.3 — Edge OPC UA Connection & Tag Read Probe (done):** `POST /api/onboarding/probe` on edge and Central. Edge dials OPC UA; Central gates (`ONBOARDING_PROBE` / admin) and forwards the validated `byte[]` to `${EDGE_BASE_URL:http://127.0.0.1:8081}` (single-edge dev assumption). Per-tag diagnostics: reachability, sample value, data type, quality `GOOD` / `BAD` / `UNREACHABLE`. Empty `nodeIds` is the M3.5 Step 2 reachability probe. Flag `app.onboarding.probe-enabled` defaults off (interim scanner gate while the bootstrap token is in the UI; M4.2 closes that). See §5.2 M3.3. Tests `EDGE-PROBE`; milestone sign-off recorded: `OnboardingProbeProsysIT` 6/6 against live Prosys (incl. `EDGE-PROBE-M33` combined `i=1001`/`i=1002`/`i=999999`; Surefire `plant-edge-service/target/surefire-reports/…OnboardingProbeProsysIT.*`); probe logs show `advertised=opc.tcp://Tanmay:53530/…` rewritten to `localhost`.

* **Step M3.4 — Single-Source Registry-to-Edge Config Sync (done — see §5.2 M3.4):** Versioned active manifest `GET /api/edge/{siteId}/config` (active lines, tag addresses, deadbands, writable flags). `plant-edge-service` fetches/syncs its active tag list on startup and on config-version updates via HTTP conditional GET polling (`If-None-Match`/`ETag`); one site per edge process (`EDGE_SITE_ID`); site-scoped MQTT command subscriptions (`site/{siteId}/line/+/commands`); last-known-good atomic JSON snapshot (`edge-config.json`) on disk so a WAN-outage restart still loads mappings (SQLite stays strictly for the telemetry WAL buffer). Hot-reload applies without restarting the OPC UA client: per-tag lenient accept/reject, parked-subscription swap, and a first-class `IDLE` phase when no tag is active. Test: add a tag in `/registry`; edge polls and publishes it with no YAML edit and no container restart; restart edge with the backend stopped and confirm snapshot fallback.

* **Step M3.5 — Guided 4-Step Onboarding Wizard in Next.js:** `/onboarding` four steps: (1) Site & Line — select existing or create; (2) Edge & Endpoint — OPC UA URL + live connection probe with empty `nodeIds`; (3) Tag Mapping Import — structured table or CSV/JSON paste, starter templates (reactor sensor/valve set), per-tag read probes chunked at ≤32 (`NODE_IDS_LIMIT_EXCEEDED`); (4) Live Telemetry Smoke Check — activate the site, line, and tags first (using the M3.3 probe for pre-activation validation), then subscribe to `/api/stream/telemetry` and confirm the first live reading arrives after the edge's next config poll. UI separates No Data Yet (Pending Activation) from Stale and BAD quality. Test: wizard E2E for `SITE-TEST` / `REACTOR-01` against Prosys — start (or restart) an edge process with `EDGE_SITE_ID=SITE-TEST` (and its own port, MQTT client id, `EDGE_CONFIG_SNAPSHOT_PATH`, and `EDGE_BUFFER_PATH` if running alongside INDORE), then land on the live console with streaming values.

* **Step M3.6 — Onboarding Playbook & Pre-Cutover Checklist:** Repeatable plant checklist: target PLC OPC UA security mode (None vs `SignAndEncrypt`), broker per-site credentials/ACLs, heartbeat and staleness, TimescaleDB retention, and edge process provisioning (exact case-sensitive `EDGE_SITE_ID`, `CENTRAL_BASE_URL`, and dedicated `EDGE_CONFIG_SNAPSHOT_PATH`, `EDGE_BUFFER_PATH`, server port, and MQTT client id when running multiple plant processes on one host). Downloadable CSV/JSON tag templates for reactors, utilities, and vessels. Test: cold-start dry run from an empty database to a live streaming reactor line using only the wizard and checklist.

### Milestone 4: Production Hardening, Reliability & Scale

* **Step M4.1 — Broker Auth, ACLs & TLS Transport:** Mosquitto `allow_anonymous false`; per-site/per-client ACLs so an edge node publishes only its own telemetry/ACKs and subscribes only to its site’s commands (M3.4 already uses site-scoped `site/{siteId}/line/+/commands`). Credentials + TLS (`ssl://` / `tls://`) on the broker, Paho clients, and the reverse proxy (Caddy) in front of web/API. Config sync stays on HTTP (no MQTT config topic to ACL); putting Caddy TLS in front of central switches `CENTRAL_BASE_URL` on the edge to `https://`. Check: unauthorized client or cross-site topic rejected; authenticated edge publishes telemetry and receives only its own site’s commands.

* **Step M4.2 — Full Authentication & RBAC Enforcement:** Spring Security on the backend; replace `BootstrapActorResolver` only — keep `AccessInterceptor`/`AccessPolicy`. Next.js login/session or token auth. Enforce `VIEWER`, `OPERATOR`, `SUPERVISOR`, `ONBOARDING_ADMIN`. Remove `NEXT_PUBLIC_BOOTSTRAP_TOKEN` / `X-Setup-Token` client path. Secure `GET /api/edge/{siteId}/config` (currently `AccessAction.OPEN` from M3.4) with a headless per-site credential (per-site API token or mTLS) wired into `plant-edge-service`, enforcing that a plant can only fetch its own `siteId`. Check: unauthenticated or `VIEWER` requests to write/registry endpoints blocked; unauthenticated or cross-site requests to `GET /api/edge/{siteId}/config` rejected (401/403); valid commands record the real operator identity in the audit log. M4.2 must close the M3.3 onboarding-probe scanner. With `NEXT_PUBLIC_BOOTSTRAP_TOKEN` baked into the UI, anyone who can load the app holds `ONBOARDING_ADMIN` and can make the edge TCP-connect to an arbitrary host:port and read arbitrary nodes; `reachable` and `error` are an open/closed-port oracle. Removing that client bootstrap token is the close. `app.onboarding.probe-enabled` (default off outside dev) is only the interim gate and stays off in production until this auth lands.

* **Step M4.3 — OPC UA Security Policies (Sign / SignAndEncrypt) & Trust Store:** Beyond None/anonymous: `Sign` / `SignAndEncrypt`, user/certificate credentials, trusted/rejected certificate store. Check: connect, subscribe, and write against a security-enabled OPC UA endpoint; untrusted certificates handled predictably.

* **Step M4.4 — Heartbeat, Staleness Detection & Command Timeout Sweeper:** Publish edge health to `site/{siteId}/edge/status` (Section 7.4), including edge phase (`UP` / `IDLE` / `BACKOFF`) and `appliedVersion` so an intentionally IDLE edge (demoted site or empty manifest) is not flagged as offline or stale in the UI; surface per-tag age/staleness in `/api/telemetry/latest`, SSE payloads, and the Next.js console when data stops or the edge is offline. `@Scheduled` sweeper moves stuck `VALIDATED_DISPATCHED` commands to `CommandStatus.TIMEOUT` via the same conditional transition as ACK handling; late-ACK behavior explicit; durable subscriptions (`cleanSession=false`) so brief disconnects do not drop commands. Check: kill edge/broker briefly and confirm staleness in the UI; let a dispatched command time out and confirm `TIMEOUT` plus audit logging.

* **Step M4.5 — TimescaleDB Lifecycle, Buffer Backpressure, Metrics & Backups:** Compression and retention policies on the telemetry hypertable so disk stays bounded (CX23). Prometheus/Actuator metrics; alert on edge offline, buffer depth, and ingest saturation; expose SQLite buffer overflow/drop counts (not log-only warnings); automated PostgreSQL/TimescaleDB backups. Check: retention/compression active; metrics report health/queue/buffer stats; backup/restore works on a test snapshot.

* **Step M4.6 — Additional Drivers & Alarm Workflow Expansion (As Needed):** Concrete S7, `MODBUS_TCP`, or `BACNET_IP` adapters/gateways only for protocols a target line actually needs. Alarm acknowledgement, shelving, and external notifications only after telemetry and command reliability are proven.

### Milestone 5: Digital Logbook (Station Shift) Integration

Station-shift digital logbook on the M1.8 command audit trail. Step breakdown follows after Milestone 4.
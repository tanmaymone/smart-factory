# Smart Factory Platform

Local development stack for the central control plane (TimescaleDB + MQTT).

For a full end-to-end dry run (Docker → Prosys → edge → backend → Next.js), rejection checks, live ACK, and tag/PLC cutover steps, see [DEMO_AND_CONFIG_PLAYBOOK.md](DEMO_AND_CONFIG_PLAYBOOK.md) and `.\scripts\Prepare-Demo.ps1`.

## Local infrastructure

Spin up PostgreSQL 16 with TimescaleDB and Eclipse Mosquitto (anonymous pub/sub on port 1883):

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

### Verify TimescaleDB extension

```bash
docker compose exec timescaledb psql -U smartfactory -d smart_factory -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'timescaledb';"
```

Expected: one row with `extname = timescaledb` and a version string.

### Verify MQTT telemetry and commands

In two terminals, subscribe to the topic wildcards used by the platform:

```bash
mosquitto_sub -h localhost -p 1883 -t "site/+/line/+/telemetry" -v
mosquitto_sub -h localhost -p 1883 -t "site/+/line/+/commands" -v
```

Publish a sample telemetry payload (QoS 1):

```bash
mosquitto_pub -h localhost -p 1883 -q 1 -t "site/INDORE/line/LINE-01/telemetry" -m "{\"messageId\":\"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d\",\"siteId\":\"INDORE\",\"lineId\":\"LINE-01\",\"timestamp\":\"2026-11-19T12:00:01.250Z\",\"readings\":[{\"tagCode\":\"VALVE_101_POS\",\"value\":75.5,\"dataType\":\"FLOAT\",\"quality\":\"GOOD\",\"sourceTimestamp\":\"2026-11-19T12:00:01.200Z\"}]}"
```

Publish a sample command payload (QoS 1):

```bash
mosquitto_pub -h localhost -p 1883 -q 1 -t "site/INDORE/line/LINE-01/commands" -m "{\"commandId\":\"c4a760a8-dbcf-4e14-9f39-645a8e937f12\",\"siteId\":\"INDORE\",\"lineId\":\"LINE-01\",\"tagCode\":\"VALVE_101_POS\",\"protocol\":\"OPC_UA\",\"externalAddress\":\"ns=3;i=1001\",\"dataType\":\"FLOAT\",\"targetValue\":50.0,\"issuedBy\":\"operator@factory.com\",\"issuedAt\":\"2026-11-19T12:05:00.000Z\"}"
```

Subscribers should print the published messages. Use [Eclipse Mosquitto clients](https://mosquitto.org/download/) (`mosquitto_sub` / `mosquitto_pub`) on the host, or run them via `docker compose exec mosquitto ...` if clients are not installed locally.

### Stop

```bash
docker compose down
```

Named volumes (`timescale_data`, `mosquitto_data`, `mosquitto_log`) retain data across restarts. Add `-v` to remove them.

## Container deploy

Three images, two machines. Docker Desktop has to be running. Prosys or a live PLC stays outside the containers. Anonymous MQTT on port 1883 is the dev broker: keep it on a VPN, and do not publish it on the public internet.

Cloud control plane (TimescaleDB, Mosquitto, central backend, Next.js):

```powershell
docker compose -f docker-compose.cloud.yml up -d --build
```

Open `http://localhost:3000/?siteId=INDORE&lineId=LINE-01`. Backend health: `http://localhost:8080/actuator/health`.

Plant PC (one container per site; pick a distinct client id). Point MQTT at the cloud broker and OPC UA at the PLC or simulator on that LAN:

```powershell
$env:MQTT_BROKER_URL = "tcp://<cloud-host>:1883"
$env:OPC_UA_ENDPOINT = "opc.tcp://<plc-host>:53530/OPCUA/SimulationServer"
$env:EDGE_MQTT_CLIENT_ID = "plant-edge-indore"
docker compose -f docker-compose.edge.yml up -d --build
```

Edge diagnostics stay on that PC: `http://127.0.0.1:8081/`. The SQLite store-and-forward file is the `edge_buffer` volume.

Laptop demo with both units, Prosys still on the Windows host (`host.docker.internal` from the edge container):

```powershell
docker compose -f docker-compose.dev.yml up -d --build
```

`OPC_UA_ENDPOINT` overrides the simulator URL when Prosys advertises a hostname. From inside a container use `host.docker.internal`, not `localhost`.

Stop a unit with the same `-f` file and `down`. Add `-v` only when you mean to drop database or edge-buffer data.

## Central backend (registry)

With TimescaleDB and Mosquitto running, start the Spring Boot control plane:

```bash
cd central-backend
# Optional: match Prosys hostname if it is not localhost
# set OPC_ENDPOINT_URL=opc.tcp://Tanmay:53530/OPCUA/SimulationServer
./mvnw spring-boot:run
```

Seeded registry checks (idempotent across restarts — row counts stay the same):

```bash
curl "http://localhost:8080/api/registry/tags"
curl "http://localhost:8080/api/registry/tags?siteId=INDORE"
curl "http://localhost:8080/api/registry/tags?siteId=INDORE&lineId=LINE-01"
```

Expect 7, 4, and 3 tags respectively. `OPC_ENDPOINT_URL` defaults to `opc.tcp://localhost:53530/OPCUA/SimulationServer`; override when Prosys advertises a machine hostname.

### Telemetry ingest and query

HTTP helper (same JSON as MQTT section 7.1 envelope or a single reading):

```bash
curl -s -X POST http://localhost:8080/api/telemetry/ingest -H "Content-Type: application/json" -d "{\"siteId\":\"INDORE\",\"lineId\":\"LINE-01\",\"timestamp\":\"2026-11-19T12:00:01.250Z\",\"readings\":[{\"tagCode\":\"REACTOR_TEMP_C\",\"value\":85.0,\"quality\":\"GOOD\",\"sourceTimestamp\":\"2026-11-19T12:00:01.200Z\"}]}"
curl "http://localhost:8080/api/telemetry/latest?siteId=INDORE&lineId=LINE-01"
curl "http://localhost:8080/api/telemetry/alarms?siteId=INDORE&lineId=LINE-01"
curl "http://localhost:8080/api/telemetry/history?siteId=INDORE&lineId=LINE-01&tagCode=REACTOR_TEMP_C"
```

Latest-tag cache defaults to in-memory (`CACHE_MODE=memory`). Set `CACHE_MODE=redis` with `REDIS_HOST` / `REDIS_PORT` to use Redis.

### Live SSE stream

Keep a stream open in one terminal (`-N` disables buffering):

```bash
curl -N "http://localhost:8080/api/stream/telemetry?siteId=INDORE&lineId=LINE-01"
```

In a second terminal, post a telemetry update (same ingest helper as above). The open stream should emit `event: TAG_UPDATE` immediately without polling. A threshold crossing (e.g. `REACTOR_TEMP_C` at `85.0`) also yields `event: ALARM_EVENT`.

Clients should treat the initial snapshot plus live events as **latest state per tag** (`siteId`/`lineId`/`tagCode`), not as ordered history — duplicates and either order around subscribe are expected. Optional filters: `siteId`, `lineId`.

## Plant edge OPC UA probe

With [Prosys OPC UA Simulation Server](https://prosysopc.com/products/opc-ua-simulation-server/) running locally (default `opc.tcp://localhost:53530/OPCUA/SimulationServer`):

```bash
cd plant-edge-service
# Optional: match the hostname Prosys advertises
# set OPC_UA_ENDPOINT=opc.tcp://Tanmay:53530/OPCUA/SimulationServer
# (OPC_ENDPOINT_URL is also accepted as a fallback)
./mvnw spring-boot:run
```

On startup the probe discovers the endpoint (rewriting host/port to the configured URL), reads the seven M1 seed tags once (`event=READ`), then applies per-tag deadband filtering before logging live changes (`event=CHANGE`) and offering them to the local SQLite store-and-forward queue. Connection failures, bad NodeIds, Bad initial reads, and Bad monitored items still fail fast. Mid-run OPC UA session loss triggers backoff reconnect (filter baseline and disk queue survive). MQTT publish to `site/{siteId}/line/{lineId}/telemetry` arrives in M2.3; until then the outbound link is a no-op so the disk queue stays empty while the broker is notionally up.

```bash
./mvnw test
```

## Web frontend

With the central backend running on port 8080:

```bash
cd web-frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). The UI defaults to `INDORE` / `LINE-01` and streams through the Next.js SSE proxy at `/api/stream/telemetry`. Override the backend origin with `BACKEND_ORIGIN` (default `http://localhost:8080`).

```bash
npm test
```

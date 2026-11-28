# Plant Onboarding Playbook (M3.6)

Operator guide for the `/onboarding` wizard, downloadable equipment templates, and the five-domain **pre-cutover checklist**. For the seeded demo stack, see [DEMO_AND_CONFIG_PLAYBOOK.md](DEMO_AND_CONFIG_PLAYBOOK.md).

## APIs (Central — `AccessAction.OPEN`)

| Method | Path | Notes |
|--------|------|--------|
| `GET` | `/api/onboarding/templates` | Catalog (`reactor`, `utilities`, `vessel`) |
| `GET` | `/api/onboarding/templates/{templateKey}?format=csv\|json` | Optional `siteId`, `lineId`, `endpointUrl`, `download` |
| `GET` | `/api/onboarding/checklist` | Optional `?category=` filter; **no** probe/edge runtime fields |
| `POST` | `/api/onboarding/probe` | Admin only (M3.3); not part of this checklist surface |

Next.js rewrites `/api/onboarding/*` to Central (`BACKEND_ORIGIN`).

## Section 1 — Pre-cutover checklist (five domains)

Domains: `OPC_UA_SECURITY`, `MQTT_BROKER_SECURITY`, `HEARTBEAT_AND_STALENESS`, `TIMESCALEDB_RETENTION`, `EDGE_PROVISIONING`.

Items with `runnableInCurrentRelease: true` are executable in M3.6. Items with `milestoneGate` `M4.1`–`M4.5` are **deferred cutover blockers**—review in `/onboarding` before production.

Walk the checklist in the UI (`PreCutoverChecklistCard`); local sign-off is stored in `sessionStorage` per site/line.

## Section 2 — Equipment tag templates

Templates ship under `central-backend/src/main/resources/onboarding/equipment-templates.json`:

- **reactor** — 5 Prosys-ready tags (`nsu=` addresses, `DRAFT` lifecycle)
- **utilities** — 6 tags
- **vessel** — 6 tags

CSV uses the exact 19 columns consumed by `POST /api/registry/tags/batch` (`TagBatchCsvParser`). Prefer `nsu=` node ids (not `ns=1;…`).

**Step 3 workflow:** pick template → Download CSV/JSON or **Load into editor** → verify parsed summary → **Save tags** → activate in Step 4.

Example download:

```powershell
curl.exe -s -o reactor-tags.csv "http://localhost:8080/api/onboarding/templates/reactor?format=csv&siteId=SITE-COLD&lineId=REACTOR-01&endpointUrl=opc.tcp://localhost:53530/OPCUA/SimulationServer"
```

## Section 3 — Multi-plant single-host matrix

When running two plant-edge JVMs on one host, isolate:

| Variable | Plant A (example) | Plant B (example) |
|----------|-------------------|-------------------|
| `EDGE_SITE_ID` | `INDORE` | `SITE-COLD` |
| `CENTRAL_BASE_URL` | `http://localhost:8080` | same |
| `EDGE_CONFIG_SNAPSHOT_PATH` | `./data/indore/edge-config.json` | `./data/site-cold/edge-config.json` |
| `EDGE_BUFFER_PATH` | `./data/indore/edge-outbound.db` | `./data/site-cold/edge-outbound.db` |
| `SERVER_PORT` (host map) | `8081` | `8082` |
| `EDGE_MQTT_CLIENT_ID` | `plant-edge-indore` | `plant-edge-site-cold` |
| `APP_ONBOARDING_PROBE_ENABLED` | `false` (enable only during onboarding) | same |

Foreign snapshots are ignored in place; command dispatch drops other sites’ MQTT topics.

## Section 4 — Cold-start empty-DB dry run

1. **Reset infra:** `docker compose down -v && docker compose up -d`
2. **Central:** `APP_REGISTRY_SEED_ENABLED=false`, `APP_BOOTSTRAP_TOKEN`, `APP_ONBOARDING_PROBE_ENABLED=true`, `EDGE_BASE_URL=http://127.0.0.1:8081`
3. **Edge:** `EDGE_SITE_ID=SITE-COLD`, isolated snapshot/buffer paths, unique MQTT client id
4. **UI:** `NEXT_PUBLIC_BOOTSTRAP_TOKEN` → `http://localhost:3000/onboarding` (empty catalog must not crash)
5. **Wizard:** create `SITE-COLD` + `REACTOR-01` → probe Step 2 → download **reactor** template → batch save → activate → verify `GET /api/edge/SITE-COLD/config` and live SSE on `/`
6. **Snapshot fallback:** stop Central, restart edge; `GET /api/edge/status` should show `config.source=LOCAL_SNAPSHOT`

Automated guardrails: `ColdStartDryRunIT`, `OnboardingTemplateApiIT`, `OnboardingChecklistApiIT`, `MultiPlantProvisioningIsolationTest` (tag `SF-M36`).
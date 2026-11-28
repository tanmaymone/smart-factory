#!/usr/bin/env bash
# Generates dev TLS material, Mosquitto passwd/acl, and seeds .env for docker-compose.dev.yml.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

CENTRAL_USER="${MQTT_CENTRAL_USERNAME:-central-backend}"
CENTRAL_PASS="${MQTT_CENTRAL_PASSWORD:-central-dev}"
HEALTH_USER="${MQTT_HEALTH_USERNAME:-healthcheck}"
HEALTH_PASS="${MQTT_HEALTH_PASSWORD:-healthcheck-dev}"
EDGE_USER="${MQTT_EDGE_USERNAME:-edge-INDORE}"
EDGE_PASS="${MQTT_EDGE_PASSWORD:-edge-indore-dev}"
PUNE_PASS="${MQTT_EDGE_PUNE_PASSWORD:-edge-pune-dev}"

CERTS_DIR="$REPO_ROOT/infra/certs"
MOSQ_DIR="$REPO_ROOT/infra/mosquitto"
mkdir -p "$CERTS_DIR"

if [[ ! -f "$CERTS_DIR/ca.crt" ]]; then
  openssl req -x509 -newkey rsa:2048 -keyout "$CERTS_DIR/ca.key" -out "$CERTS_DIR/ca.crt" \
    -days 825 -nodes -subj "/CN=Smart Factory Dev CA"
fi

make_server_cert() {
  local name="$1"
  shift
  local ext="$CERTS_DIR/$name.ext"
  local san="subjectAltName = DNS:localhost"
  for d in "$@"; do
  san="$san, DNS:$d"
  done
  san="$san, IP:127.0.0.1"
  echo "$san" > "$ext"
  openssl req -newkey rsa:2048 -keyout "$CERTS_DIR/$name.key" -out "$CERTS_DIR/$name.csr" \
    -nodes -subj "/CN=$name"
  openssl x509 -req -in "$CERTS_DIR/$name.csr" -CA "$CERTS_DIR/ca.crt" -CAkey "$CERTS_DIR/ca.key" \
    -CAcreateserial -out "$CERTS_DIR/$name.crt" -days 825 -extfile "$ext"
  rm -f "$CERTS_DIR/$name.csr" "$ext"
}

make_server_cert broker mosquitto localhost host.docker.internal
make_server_cert caddy caddy localhost host.docker.internal

docker run --rm -v "$MOSQ_DIR:/out" eclipse-mosquitto:2 sh -c \
  "mosquitto_passwd -b -c /out/passwd '$CENTRAL_USER' '$CENTRAL_PASS' && \
   mosquitto_passwd -b /out/passwd '$HEALTH_USER' '$HEALTH_PASS' && \
   mosquitto_passwd -b /out/passwd edge-INDORE '$EDGE_PASS' && \
   mosquitto_passwd -b /out/passwd edge-PUNE '$PUNE_PASS'"

if [[ ! -f "$MOSQ_DIR/acl" && -f "$MOSQ_DIR/acl.example" ]]; then
  cp "$MOSQ_DIR/acl.example" "$MOSQ_DIR/acl"
fi

ENV_FILE="$REPO_ROOT/.env"
if [[ ! -f "$ENV_FILE" && -f "$REPO_ROOT/.env.example" ]]; then
  cp "$REPO_ROOT/.env.example" "$ENV_FILE"
fi
touch "$ENV_FILE"

ensure_env() {
  local key="$1" val="$2"
  if ! grep -qE "^[[:space:]]*${key}[[:space:]]*=" "$ENV_FILE" 2>/dev/null; then
    echo "${key}=${val}" >> "$ENV_FILE"
  fi
}

ensure_env MQTT_CENTRAL_PASSWORD "$CENTRAL_PASS"
ensure_env MQTT_PASSWORD "$EDGE_PASS"
ensure_env MQTT_USERNAME "$EDGE_USER"
ensure_env MQTT_EDGE_USERNAME "$EDGE_USER"
ensure_env MQTT_EDGE_PASSWORD "$EDGE_PASS"
ensure_env MQTT_HEALTH_PASSWORD "$HEALTH_PASS"

echo "Dev TLS generated under infra/certs; passwd and acl under infra/mosquitto."
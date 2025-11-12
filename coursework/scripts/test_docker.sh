#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
DC_FILE="$ROOT_DIR/docker/docker-compose.yml"

echo "[test] starting docker compose" && echo
docker compose -f "$DC_FILE" up -d

echo "[test] waiting for postgres to be healthy"
for i in {1..60}; do
  status=$(docker inspect -f '{{.State.Health.Status}}' kk_pg 2>/dev/null || echo "unknown")
  if [[ "$status" == "healthy" ]]; then
    echo "[test] postgres is healthy"
    break
  fi
  sleep 1
done

if [[ "$status" != "healthy" ]]; then
  echo "[test] postgres did not become healthy in time (status=$status)" >&2
  exit 1
fi

export PGHOST=127.0.0.1
export PGPORT=5433
export PGUSER=postgres
export PGPASSWORD=postgres
export DC_FILE="$DC_FILE"

DB_NAME=krusty_crm

echo && echo "[test] running create_db.sh"
"$ROOT_DIR/scripts/create_db.sh"

echo && echo "[test] running seed_db.sh"
"$ROOT_DIR/scripts/seed_db.sh"

echo && echo "[test] sample query"
docker compose -f "$DC_FILE" exec -T \
  -e PGPASSWORD="${PGPASSWORD:-postgres}" \
  db psql -h 127.0.0.1 -p 5432 -U "${PGUSER:-postgres}" \
  -Atq -d "$DB_NAME" -c "select count(*) from orders;"

echo && echo "[test] running drop_db.sh"
"$ROOT_DIR/scripts/drop_db.sh"

echo && echo "[test] done"

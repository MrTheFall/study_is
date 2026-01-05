#!/usr/bin/env bash
set -euo pipefail

DB_NAME=krusty_crm

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

DC_FILE=${DC_FILE:-"$ROOT_DIR/docker/docker-compose.yml"}

echo "[seed_db] ensuring docker compose is up"
docker compose -f "$DC_FILE" up -d

echo "[seed_db] waiting for postgres to be healthy"
for i in {1..60}; do
  status=$(docker inspect -f '{{.State.Health.Status}}' kk_pg 2>/dev/null || echo "unknown")
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 1
done

if [[ "${status:-unknown}" != "healthy" ]]; then
  echo "[seed_db] postgres container is not healthy (status=$status)" >&2
  exit 1
fi

echo "[seed_db] applying db/seed.sql to $DB_NAME"
docker compose -f "$DC_FILE" exec -T \
  -e PGPASSWORD="${PGPASSWORD:-postgres}" \
  db psql -h 127.0.0.1 -p 5432 -U "${PGUSER:-postgres}" \
  -v ON_ERROR_STOP=1 -d "$DB_NAME" -f - < "$ROOT_DIR/db/seed.sql"

echo "[seed_db] syncing sequences"
docker compose -f "$DC_FILE" exec -T \
  -e PGPASSWORD="${PGPASSWORD:-postgres}" \
  db psql -h 127.0.0.1 -p 5432 -U "${PGUSER:-postgres}" \
  -v ON_ERROR_STOP=1 -d "$DB_NAME" -f - < "$ROOT_DIR/db/sync_sequences.sql"

echo "[seed_db] done"

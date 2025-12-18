#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

DB_NAME=krusty_crm
DC_FILE=${DC_FILE:-"$ROOT_DIR/docker/docker-compose.yml"}

echo "[update_functions] Applying function updates to database: $DB_NAME"

echo "[update_functions] ensuring docker compose is up"
docker compose -f "$DC_FILE" up -d

echo "[update_functions] waiting for postgres to be healthy"
for i in {1..60}; do
  status=$(docker inspect -f '{{.State.Health.Status}}' kk_pg 2>/dev/null || echo "unknown")
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 1
done

if [[ "${status:-unknown}" != "healthy" ]]; then
  echo "[update_functions] postgres container is not healthy (status=$status)" >&2
  exit 1
fi

run_psql() {
  docker compose -f "$DC_FILE" exec -T \
    -e PGPASSWORD="${PGPASSWORD:-postgres}" \
    db psql -h 127.0.0.1 -p 5432 -U "${PGUSER:-postgres}" "$@"
}

run_psql -v ON_ERROR_STOP=1 -q -d postgres -c 'select 1;' >/dev/null

run_psql -v ON_ERROR_STOP=1 -q -d "$DB_NAME" -f - < "$ROOT_DIR/db/functions.sql"

echo "[update_functions] Functions updated successfully"

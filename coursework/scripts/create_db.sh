#!/usr/bin/env bash
set -euo pipefail

DB_NAME=krusty_crm

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

echo "[create_db] target database: $DB_NAME"

DC_FILE=${DC_FILE:-"$ROOT_DIR/docker/docker-compose.yml"}

echo "[create_db] ensuring docker compose is up"
docker compose -f "$DC_FILE" up -d

echo "[create_db] waiting for postgres to be healthy"
for i in {1..60}; do
  status=$(docker inspect -f '{{.State.Health.Status}}' kk_pg 2>/dev/null || echo "unknown")
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 1
done

if [[ "${status:-unknown}" != "healthy" ]]; then
  echo "[create_db] postgres container is not healthy (status=$status)" >&2
  exit 1
fi

run_psql() {
  docker compose -f "$DC_FILE" exec -T \
    -e PGPASSWORD="${PGPASSWORD:-postgres}" \
    db psql -h 127.0.0.1 -p 5432 -U "${PGUSER:-postgres}" "$@"
}

run_psql -v ON_ERROR_STOP=1 -q -d postgres -c 'select 1;' >/dev/null

EXISTS=$(run_psql -Atqc "select 1 from pg_database where datname='${DB_NAME}'" postgres || true)
if [[ "$EXISTS" != "1" ]]; then
  echo "[create_db] creating database $DB_NAME"
  run_psql -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE \"$DB_NAME\";"
else
  echo "[create_db] database already exists"
fi

echo "[create_db] applying db/schema.sql"
run_psql -v ON_ERROR_STOP=1 -d "$DB_NAME" -f - < "$ROOT_DIR/db/schema.sql"

if [[ -f "$ROOT_DIR/db/functions.sql" ]]; then
  echo "[create_db] applying db/functions.sql"
  run_psql -v ON_ERROR_STOP=1 -d "$DB_NAME" -f - < "$ROOT_DIR/db/functions.sql"
fi

echo "[create_db] done"

#!/usr/bin/env bash
set -euo pipefail

DB_NAME=krusty_crm

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

echo "[drop_db] target database: $DB_NAME"

DC_FILE=${DC_FILE:-"$ROOT_DIR/docker/docker-compose.yml"}

echo "[drop_db] ensuring docker compose is up"
docker compose -f "$DC_FILE" up -d

echo "[drop_db] waiting for postgres to be healthy"
for i in {1..60}; do
  status=$(docker inspect -f '{{.State.Health.Status}}' kk_pg 2>/dev/null || echo "unknown")
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 1
done

if [[ "${status:-unknown}" != "healthy" ]]; then
  echo "[drop_db] postgres container is not healthy (status=$status)" >&2
  exit 1
fi

run_psql() {
  docker compose -f "$DC_FILE" exec -T \
    -e PGPASSWORD="${PGPASSWORD:-postgres}" \
    db psql -h 127.0.0.1 -p 5432 -U "${PGUSER:-postgres}" "$@"
}

run_psql -v ON_ERROR_STOP=1 -q -d postgres -c 'select 1;' >/dev/null

echo "[drop_db] terminating connections"
run_psql -v ON_ERROR_STOP=1 -d postgres -c "\
  REVOKE CONNECT ON DATABASE \"$DB_NAME\" FROM PUBLIC;\
  SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME';\
" || true

echo "[drop_db] dropping database if exists"
run_psql -v ON_ERROR_STOP=1 -d postgres -c "DROP DATABASE IF EXISTS \"$DB_NAME\";"

echo "[drop_db] done"

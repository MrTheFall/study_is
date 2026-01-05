#!/usr/bin/env bash
set -euo pipefail

DB_NAME=krusty_crm

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

DC_FILE=${DC_FILE:-"$ROOT_DIR/docker/docker-compose.yml"}
MIGRATIONS_DIR=${MIGRATIONS_DIR:-"$ROOT_DIR/db/migrations"}

echo "[migrate_db] target database: $DB_NAME"
echo "[migrate_db] migrations dir: $MIGRATIONS_DIR"

echo "[migrate_db] ensuring docker compose is up"
docker compose -f "$DC_FILE" up -d

echo "[migrate_db] waiting for postgres to be healthy"
for i in {1..60}; do
  status=$(docker inspect -f '{{.State.Health.Status}}' kk_pg 2>/dev/null || echo "unknown")
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 1
done

if [[ "${status:-unknown}" != "healthy" ]]; then
  echo "[migrate_db] postgres container is not healthy (status=$status)" >&2
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
  echo "[migrate_db] database $DB_NAME does not exist; run ./scripts/create_db.sh first" >&2
  exit 1
fi

run_psql -v ON_ERROR_STOP=1 -q -d "$DB_NAME" -c "\
  create table if not exists schema_migrations (\
    id bigserial primary key,\
    filename text not null unique,\
    checksum text not null,\
    applied_at timestamp not null default now()\
  );\
" >/dev/null

if [[ ! -d "$MIGRATIONS_DIR" ]]; then
  echo "[migrate_db] migrations dir does not exist; nothing to do"
  exit 0
fi

shopt -s nullglob
files=("$MIGRATIONS_DIR"/*.sql)
shopt -u nullglob

if [[ ${#files[@]} -eq 0 ]]; then
  echo "[migrate_db] no migrations found; nothing to do"
  exit 0
fi

LC_ALL=C
for file in "${files[@]}"; do
  name=$(basename "$file")
  checksum=$(sha256sum "$file" | awk '{print $1}')
  esc_name=${name//\'/\'\'}
  current=$(run_psql -Atqc "select checksum from schema_migrations where filename='${esc_name}'" "$DB_NAME" || true)

  if [[ -n "$current" ]]; then
    if [[ "$current" != "$checksum" ]]; then
      echo "[migrate_db] checksum mismatch for $name" >&2
      echo "  db:   $current" >&2
      echo "  file: $checksum" >&2
      echo "[migrate_db] Do not edit applied migrations; create a new migration file instead." >&2
      exit 1
    fi
    echo "[migrate_db] skip $name (already applied)"
    continue
  fi

  echo "[migrate_db] applying $name"
  run_psql -v ON_ERROR_STOP=1 -d "$DB_NAME" -f - < "$file"
  run_psql -v ON_ERROR_STOP=1 -q -d "$DB_NAME" -c "\
    insert into schema_migrations(filename, checksum) values ('${esc_name}', '${checksum}');\
  " >/dev/null
  echo "[migrate_db] applied $name"
done

echo "[migrate_db] done"


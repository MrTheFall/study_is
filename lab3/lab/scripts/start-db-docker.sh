#!/usr/bin/env bash
set -euo pipefail

PG_CONTAINER=${PG_CONTAINER:-orgmgr-pg}
PG_DB=${PG_DB:-studs}
PG_USER=${PG_USER:-postgres}
PG_PASSWORD=${PG_PASSWORD:-postgres}
PG_PORT=${PG_PORT:-5432}

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
COMPOSE_FILE=${COMPOSE_FILE:-"$SCRIPT_DIR/../docker-compose.db.yml"}
export PG_CONTAINER PG_DB PG_USER PG_PASSWORD PG_PORT

docker compose -f "$COMPOSE_FILE" up -d postgres >/dev/null

echo -n 'Waiting for Postgres to be ready'
for _ in $(seq 1 60); do
  if docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; then
    echo ' ✔'
    exit 0
  fi
  echo -n ' .'
  sleep 1
done

echo ' ✖ (timeout)'
exit 1

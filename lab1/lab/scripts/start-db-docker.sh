#!/usr/bin/env bash
set -euo pipefail

PG_CONTAINER=${PG_CONTAINER:-orgmgr-pg}
PG_DB=${PG_DB:-studs}
PG_USER=${PG_USER:-postgres}
PG_PASSWORD=${PG_PASSWORD:-postgres}
PG_PORT=${PG_PORT:-5432}

if ! command -v docker >/dev/null 2>&1; then
  echo 'Docker not found. Skipping DB container start.'
  exit 0
fi

docker stop "$PG_CONTAINER" >/dev/null 2>&1 || true

if ! docker ps -a --format '{{.Names}}' | grep -qx "$PG_CONTAINER"; then
  echo "Running postgres container $PG_CONTAINER..."
  docker run -d --name "$PG_CONTAINER" \
    -e POSTGRES_DB="$PG_DB" \
    -e POSTGRES_USER="$PG_USER" \
    -e POSTGRES_PASSWORD="$PG_PASSWORD" \
    -p "$PG_PORT":5432 postgres:16 >/dev/null
else
  docker start "$PG_CONTAINER" >/dev/null
fi

echo -n 'Waiting for Postgres to be ready'
for _ in $(seq 1 60); do
  if docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; then
    echo ' ✔'
    exit 0
  fi
  echo -n ' .'
  sleep 1
done

echo ' ✖ (timeout)'
exit 1

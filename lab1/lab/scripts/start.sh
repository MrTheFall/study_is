#!/usr/bin/env bash
set -euo pipefail

# Start script: clean DB, init schema, start web app (Gradle)
#
# Usage:
#  ./scripts/start.sh            # uses Dockerized Postgres on localhost:5432
#  SPRING_DATASOURCE_URL=... \   # override DB connection
#  SPRING_DATASOURCE_USERNAME=... \
#  SPRING_DATASOURCE_PASSWORD=... ./scripts/start.sh

APP_NAME="org-manager"
DB_NAME="studs"
DB_USER="${SPRING_DATASOURCE_USERNAME:-student}"
DB_PASS="${SPRING_DATASOURCE_PASSWORD:-student}"
DB_URL="${SPRING_DATASOURCE_URL:-}"
USE_DOCKER_DB=0

# Detect whether to run DB via Docker (default when DB_URL is empty)
if [[ -z "${DB_URL}" ]]; then
  if command -v docker >/dev/null 2>&1; then
    USE_DOCKER_DB=1
    DB_URL="jdbc:postgresql://localhost:5432/${DB_NAME}"
  else
    echo "No SPRING_DATASOURCE_URL provided and docker not found."
    echo "Set SPRING_DATASOURCE_URL (e.g. jdbc:postgresql://pg:5432/studs) and rerun."
    exit 1
  fi
fi

run_psql() {
  local sql_file="$1"
  if (( USE_DOCKER_DB == 1 )); then
    docker exec -i orgmgr-pg psql -U "$DB_USER" -d "$DB_NAME" < "$sql_file"
  else
    # Expect psql available locally
    if ! command -v psql >/dev/null 2>&1; then
      echo "psql is required to apply schema locally. Install PostgreSQL client or use Docker."
      exit 1
    fi
    PGPASSWORD="$DB_PASS" psql -h "$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^:/]+):?([0-9]*)/.*#\1#')" \
      -p "$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://[^:/]+:([0-9]+)/.*#\1#' | sed 's/^jdbc.*//' )" \
      -U "$DB_USER" -d "$DB_NAME" < "$sql_file"
  fi
}

echo "==> Ensuring PostgreSQL is running (${DB_NAME})"
if (( USE_DOCKER_DB == 1 )); then
  if ! docker ps -a --format '{{.Names}}' | grep -qx orgmgr-pg; then
    docker run -d --name orgmgr-pg -e POSTGRES_DB="$DB_NAME" -e POSTGRES_PASSWORD="$DB_PASS" -e POSTGRES_USER="$DB_USER" -p 5432:5432 postgres:16 >/dev/null
  else
    docker start orgmgr-pg >/dev/null
  fi
  echo -n "Waiting for Postgres to be ready"
  for i in $(seq 1 60); do
    if docker exec orgmgr-pg pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      echo " ✔"; break; fi; echo -n " ."; sleep 1; done
fi

echo "==> Cleaning database"
TMP_SQL=$(mktemp)
cat > "$TMP_SQL" <<'SQL'
-- Drop existing objects (order with CASCADE)
drop table if exists organization cascade;
drop table if exists address cascade;
drop table if exists coordinates cascade;
SQL
run_psql "$TMP_SQL"
rm -f "$TMP_SQL"

echo "==> Applying schema: src/main/resources/db/schema.sql"
run_psql "src/main/resources/db/schema.sql"

echo "==> Building application (Gradle)"
./gradlew --no-daemon -q build -x test

echo "==> Starting web app"
mkdir -p run
APP_LOG="run/app.log"
if pgrep -f "org.springframework.boot.loader.JarLauncher" >/dev/null 2>&1; then
  pkill -f "org.springframework.boot.loader.JarLauncher" || true
fi
if pgrep -f "org-manager-0.0.1-SNAPSHOT.jar" >/dev/null 2>&1; then
  pkill -f "org-manager-0.0.1-SNAPSHOT.jar" || true
fi

# Stop current
./gradlew --stop
# Prefer bootRun to enable dev reloads
SPRING_DATASOURCE_URL="$DB_URL" SPRING_DATASOURCE_USERNAME="$DB_USER" SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
  nohup ./gradlew --no-daemon bootRun > "$APP_LOG" 2>&1 &
echo $! > run/app.pid

APP_WAIT_URL=${APP_WAIT_URL:-http://localhost:8080/organizations}
echo -n "==> Waiting for ${APP_WAIT_URL}"
READY=0
for i in $(seq 1 120); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$APP_WAIT_URL" || echo 000)
  if [[ "$CODE" =~ ^[0-9]{3}$ ]]; then
    if [ "$CODE" -ge 200 ] && [ "$CODE" -lt 400 ]; then READY=1; break; fi
  fi
  if ss -ltn 2>/dev/null | grep -q ":8080 "; then READY=1; break; fi
  echo -n " ."; sleep 1
done

if [ "$READY" -eq 1 ]; then
  echo " ✔";
else
  echo " ✖ (timed out)";
  echo "Hint: check logs: tail -n 200 run/app.log";
fi

echo "==> Open: http://localhost:8080"
echo "    Logs: tail -f $APP_LOG"
echo "    Stop: ./gradlew --stop && ( test -f run/app.pid && kill \"$(cat run/app.pid)\" 2>/dev/null || true )"

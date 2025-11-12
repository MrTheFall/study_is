#!/usr/bin/env python3
import os
import re
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
DC_FILE = os.environ.get("DC_FILE", str(ROOT / "docker" / "docker-compose.yml"))
DB_NAME = "krusty_crm"
PGUSER = os.environ.get("PGUSER", "postgres")
PGPASSWORD = os.environ.get("PGPASSWORD", "postgres")
PGHOST = "127.0.0.1"
PGPORT = "5432"


def run(cmd: list[str], input_bytes: bytes | None = None, check=True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, input=input_bytes, check=check, capture_output=True)


def docker_compose(args: list[str]) -> None:
    cmd = ["docker", "compose", "-f", DC_FILE] + args
    cp = run(cmd)
    if cp.stdout:
        sys.stdout.write(cp.stdout.decode())
    if cp.stderr:
        sys.stdout.write(cp.stderr.decode())


def ensure_up() -> None:
    docker_compose(["up", "-d"])  # start db
    # wait for health
    for _ in range(60):
        cp = run(["docker", "inspect", "-f", "{{.State.Health.Status}}", "kk_pg"], check=False)
        status = cp.stdout.decode().strip() if cp.returncode == 0 else "unknown"
        if status == "healthy":
            return
        time.sleep(1)
    raise RuntimeError(f"postgres container not healthy (status={status})")


def psql_args(db: str, extra: list[str]) -> list[str]:
    return [
        "docker", "compose", "-f", DC_FILE, "exec", "-T",
        "-e", f"PGPASSWORD={PGPASSWORD}",
        "db", "psql",
        "-h", PGHOST, "-p", PGPORT, "-U", PGUSER, "-d", db,
        "-v", "ON_ERROR_STOP=1",
    ] + extra


def psql_exec(db: str, sql: str) -> str:
    cmd = psql_args(db, ["-c", sql])
    cp = run(cmd, check=True)
    return (cp.stdout or b"").decode()


def psql_apply_file(db: str, path: Path) -> None:
    sql = path.read_bytes()
    cmd = psql_args(db, ["-f", "-"])
    run(cmd, input_bytes=sql)


def psql_value(db: str, sql: str) -> str:
    cmd = psql_args(db, ["-Atq", "-c", sql])
    cp = run(cmd, check=True)
    return (cp.stdout or b"").decode().strip()


def recreate_db() -> None:
    psql_exec("postgres", f"DROP DATABASE IF EXISTS \"{DB_NAME}\";")
    psql_exec("postgres", f"CREATE DATABASE \"{DB_NAME}\";")


def parse_exec_time(explain_text: str) -> float:
    # Find last "Execution Time: X ms"
    times = [float(m.group(1)) for m in re.finditer(r"Execution Time:\s+([0-9.]+)\s+ms", explain_text)]
    if not times:
        raise ValueError("Execution Time not found in EXPLAIN output")
    return times[-1]


def explain_analyze(db: str, sql: str) -> float:
    out = psql_exec(db, f"EXPLAIN (ANALYZE, BUFFERS, TIMING, SUMMARY) {sql};")
    return parse_exec_time(out)


def avg3(db: str, sql: str) -> float:
    # warm-up
    explain_analyze(db, sql)
    t = [explain_analyze(db, sql) for _ in range(3)]
    return round(sum(t) / 3.0, 3)


def bench(name: str, query: str, create_idx: str, size_query: str | None) -> None:
    print(f"[pybench] === {name} ===")
    psql_exec(DB_NAME, "VACUUM (ANALYZE);")
    before = avg3(DB_NAME, query)
    print(f"[pybench] before index: {before} ms")
    if create_idx:
        psql_exec(DB_NAME, create_idx)
    psql_exec(DB_NAME, "VACUUM (ANALYZE);")
    after = avg3(DB_NAME, query)
    print(f"[pybench] after index:  {after} ms")
    if size_query:
        size = psql_value(DB_NAME, size_query)
        print(f"[pybench] index size:   {size}")


def main() -> None:
    ensure_up()
    print("[pybench] recreating database")
    recreate_db()
    print("[pybench] applying schema and functions")
    psql_apply_file(DB_NAME, ROOT / "db" / "schema.sql")
    psql_apply_file(DB_NAME, ROOT / "db" / "functions.sql")
    print("[pybench] generating data (this may take ~30-60s)")
    psql_apply_file(DB_NAME, ROOT / "db" / "generate_test_data.sql")

    # 1) Kitchen queue
    psql_exec(DB_NAME, "DROP INDEX IF EXISTS idx_orders_status;")
    bench(
        "orders: status IN (...) ORDER BY created_at LIMIT 5000",
        "select o.id from orders o where o.status in ('confirmed','preparing') order by o.created_at asc limit 5000",
        "create index if not exists idx_orders_status_created_at on orders(status, created_at)",
        "select pg_size_pretty(pg_relation_size('idx_orders_status_created_at'))",
    )

    # 2) Client history (covering)
    cid = psql_value(DB_NAME, "select client_id from orders group by client_id order by count(*) desc limit 1;")
    bench(
        f"orders: client_id={cid} ORDER BY created_at DESC LIMIT 1000 (covering)",
        f"select id from orders where client_id = {cid} order by created_at desc limit 1000",
        "create index if not exists idx_orders_client_created_at_id on orders(client_id, created_at desc, id)",
        "select pg_size_pretty(pg_relation_size('idx_orders_client_created_at_id'))",
    )

    # 3) Payments last 30d
    from_ts = psql_value(DB_NAME, "select (now() - interval '30 days')::timestamp;")
    to_ts = psql_value(DB_NAME, "select now()::timestamp;")
    bench(
        "payments: success AND paid_at BETWEEN last 30d",
        f"select sum(amount) from payments where success = true and paid_at between '{from_ts}' and '{to_ts}'",
        "create index if not exists idx_payments_paid_at_success on payments(paid_at) where success = true",
        "select pg_size_pretty(pg_relation_size('idx_payments_paid_at_success'))",
    )

    # 4) Menu search
    psql_exec(DB_NAME, "create extension if not exists pg_trgm;")
    bench(
        "menu_items: name ILIKE '%Patty%'",
        "select count(*) from menu_items where name ilike '%Patty%'",
        "create index if not exists idx_menu_items_name_trgm on menu_items using gin (name gin_trgm_ops)",
        "select pg_size_pretty(pg_relation_size('idx_menu_items_name_trgm'))",
    )


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as e:
        sys.stderr.write(e.stderr.decode() if e.stderr else str(e))
        sys.exit(1)

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)

SKIP_BUILD=false
if [[ ${1:-} == "--skip-build" ]]; then
  SKIP_BUILD=true
  shift
fi

REMOTE_USER=${DEPLOY_USER:-root}
REMOTE_HOST=${DEPLOY_HOST:-helios.cs.ifmo.ru}
REMOTE_PORT=${DEPLOY_PORT:-2222}
TARGET="${REMOTE_USER}@${REMOTE_HOST}"

if ! command -v scp >/dev/null 2>&1; then
  echo 'scp not found on PATH.' >&2
  exit 1
fi

pushd "${PROJECT_ROOT}" >/dev/null
if [[ "${SKIP_BUILD}" == "false" ]]; then
  ./gradlew bootJar
fi

# Prefer the latest non-plain boot jar built by Gradle (look inside modules first)
jar_path=$(ls -1t app/build/libs/*.jar 2>/dev/null | grep -v -- '-plain\\.jar$' | head -n1 || true)
if [[ -z "${jar_path:-}" ]]; then
  jar_path=$(ls -1t build/libs/*.jar 2>/dev/null | grep -v -- '-plain\\.jar$' | head -n1 || true)
fi
popd >/dev/null

if [[ -z "${jar_path:-}" ]]; then
  echo 'Boot jar not found in module build/libs directories' >&2
  exit 1
fi

jar_abs_path="${PROJECT_ROOT}/${jar_path}"

if [[ ! -f "${jar_abs_path}" ]]; then
  echo "Boot jar not found at ${jar_abs_path}" >&2
  exit 1
fi

echo "Deploying ${jar_abs_path} to ${TARGET} on port ${REMOTE_PORT}"
scp -P "${REMOTE_PORT}" "${jar_abs_path}" "${TARGET}:~/"

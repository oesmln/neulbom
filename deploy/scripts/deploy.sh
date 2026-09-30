#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
deploy_env_file="${DEPLOY_ENV_FILE:-${repo_root}/deploy/.env}"
compose_file="${repo_root}/deploy/compose.prod.yml"

if [[ ! -f "${deploy_env_file}" ]]; then
    echo "Missing deployment environment file: ${deploy_env_file}" >&2
    exit 1
fi

# Existing production installations pin the former CPU timeout in deploy/.env.
# Migrate only that exact value; preserve intentional custom limits.
if grep -qx 'AI_SERVER_ANALYSIS_PROCESSING_TIMEOUT_SECONDS=300' "${deploy_env_file}"; then
    sed -i 's/^AI_SERVER_ANALYSIS_PROCESSING_TIMEOUT_SECONDS=300$/AI_SERVER_ANALYSIS_PROCESSING_TIMEOUT_SECONDS=900/' "${deploy_env_file}"
fi

cd "${repo_root}"

docker compose --env-file "${deploy_env_file}" -f "${compose_file}" config --quiet
docker compose --env-file "${deploy_env_file}" -f "${compose_file}" build --pull
docker compose --env-file "${deploy_env_file}" -f "${compose_file}" up --detach --remove-orphans
docker compose --env-file "${deploy_env_file}" -f "${compose_file}" ps

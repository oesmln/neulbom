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

cd "${repo_root}"

docker compose --env-file "${deploy_env_file}" -f "${compose_file}" config --quiet
docker compose --env-file "${deploy_env_file}" -f "${compose_file}" build --pull
docker compose --env-file "${deploy_env_file}" -f "${compose_file}" up --detach --remove-orphans
docker compose --env-file "${deploy_env_file}" -f "${compose_file}" ps

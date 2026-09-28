#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <commit-SHA>" >&2
    exit 1
fi

target_revision="$1"
releases_root="${DEPLOY_RELEASES_ROOT:-/opt/neulbom/releases}"
app_dir="${DEPLOY_APP_DIR:-/opt/neulbom/app}"
deploy_env_file="${DEPLOY_ENV_FILE:-${app_dir}/deploy/.env}"
compose_file="${releases_root}/${target_revision}/deploy/compose.prod.yml"

if [[ ! -f "${deploy_env_file}" ]]; then
    echo "Missing deployment environment file: ${deploy_env_file}" >&2
    exit 1
fi

if [[ ! -f "${compose_file}" ]]; then
    echo "Release not found: ${releases_root}/${target_revision}" >&2
    exit 1
fi

compose=(docker compose --env-file "${deploy_env_file}" -f "${compose_file}")
IMAGE_TAG="${target_revision}" "${compose[@]}" up --detach --no-build --remove-orphans

expected_services=(nginx backend ai-server postgres)
healthy=0
for attempt in {1..60}; do
    healthy=1
    for service in "${expected_services[@]}"; do
        container="$("${compose[@]}" ps -q "${service}")"
        if [[ -z "${container}" ]]; then
            healthy=0
            break
        fi
        status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container}")"
        if [[ "${status}" != healthy ]]; then
            healthy=0
            break
        fi
    done
    if [[ "${healthy}" -eq 1 ]]; then
        break
    fi
    sleep 5
done

if [[ "${healthy}" -ne 1 ]]; then
    "${compose[@]}" ps
    exit 1
fi

"${compose[@]}" exec -T backend curl --fail --silent --show-error \
    http://127.0.0.1:8080/actuator/health/readiness > /dev/null
"${compose[@]}" exec -T backend ffmpeg -version > /dev/null
"${compose[@]}" exec -T ai-server python -c \
    'import urllib.request; urllib.request.urlopen("http://127.0.0.1:8000/health/ready", timeout=5)'

if grep -q '^IMAGE_TAG=' "${deploy_env_file}"; then
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${target_revision}/" "${deploy_env_file}"
else
    printf 'IMAGE_TAG=%s\n' "${target_revision}" >> "${deploy_env_file}"
fi
chmod 600 "${deploy_env_file}"
printf '%s\n' "${target_revision}" > "${app_dir}/.deploy-revision"

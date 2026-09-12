#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <duckdns-domain>" >&2
    exit 1
fi

certificate_domain="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
deploy_env_file="${DEPLOY_ENV_FILE:-${repo_root}/deploy/.env}"
compose_file="${repo_root}/deploy/compose.prod.yml"
certificate_dir="/etc/letsencrypt/live/${certificate_domain}"
certificate_target="${repo_root}/../certs"

sudo certbot renew --quiet
sudo mkdir -p "${certificate_target}"
sudo cp -L "${certificate_dir}/fullchain.pem" "${certificate_target}/fullchain.pem"
sudo cp -L "${certificate_dir}/privkey.pem" "${certificate_target}/privkey.pem"
sudo chmod 644 "${certificate_target}/fullchain.pem"
sudo chmod 600 "${certificate_target}/privkey.pem"

docker compose --env-file "${deploy_env_file}" -f "${compose_file}" restart nginx


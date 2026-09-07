#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 || "$1" != https://* ]]; then
  echo "Usage: $0 https://your-public-backend-origin" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
backend_env="$repo_dir/backend/.env"
ai_env="$repo_dir/ai-server/.env"
public_origin="${1%/}"

if [[ ! -f "$backend_env" ]]; then
  cp "$repo_dir/backend/.env.example" "$backend_env"
fi
if [[ ! -f "$ai_env" ]]; then
  cp "$repo_dir/ai-server/.env.example" "$ai_env"
fi

update_env() {
  local file="$1"
  local key="$2"
  local value="$3"
  local temporary
  temporary="$(mktemp "${TMPDIR:-/tmp}/neulbom-env.XXXXXX")"
  awk -v key="$key" -v value="$value" '
    BEGIN { found = 0 }
    index($0, key "=") == 1 {
      if (!found) print key "=" value
      found = 1
      next
    }
    { print }
    END { if (!found) print key "=" value }
  ' "$file" > "$temporary"
  mv "$temporary" "$file"
}

service_token="$(openssl rand -hex 32)"
signing_secret="$(openssl rand -hex 32)"

update_env "$ai_env" AI_SERVER_SERVICE_TOKEN "$service_token"
update_env "$backend_env" AI_SERVER_ENABLED true
update_env "$backend_env" AI_SERVER_BASE_URL http://localhost:8000
update_env "$backend_env" AI_SERVER_SERVICE_TOKEN "$service_token"
update_env "$backend_env" AI_AUDIO_PUBLIC_BASE_URL "$public_origin"
update_env "$backend_env" AI_AUDIO_SIGNING_SECRET "$signing_secret"

echo "Configured backend/.env and ai-server/.env without printing secrets."
echo "Restart both servers so the rotated values take effect."

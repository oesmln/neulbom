#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <git-commit-or-tag>" >&2
    exit 1
fi

target_revision="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"

cd "${repo_root}"
git fetch --tags origin
git switch --detach "${target_revision}"
"${script_dir}/deploy.sh"

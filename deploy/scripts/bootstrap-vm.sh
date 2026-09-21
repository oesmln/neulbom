#!/usr/bin/env bash

set -Eeuo pipefail

data_device="/dev/disk/by-id/google-neulbom-data"
data_label="neulbom-data"
mount_point="/opt/neulbom"

for _ in {1..30}; do
    [[ -e "${data_device}" ]] && break
    sleep 2
done

if [[ ! -e "${data_device}" ]]; then
    echo "Persistent data disk was not attached: ${data_device}" >&2
    exit 1
fi

if ! blkid "${data_device}" >/dev/null 2>&1; then
    mkfs.ext4 -F -L "${data_label}" "${data_device}"
fi

mkdir -p "${mount_point}"
if ! grep -q "LABEL=${data_label}" /etc/fstab; then
    echo "LABEL=${data_label} ${mount_point} ext4 defaults,nofail 0 2" >> /etc/fstab
fi
mount -a

mkdir -p \
    "${mount_point}/acme" \
    "${mount_point}/app" \
    "${mount_point}/certs" \
    "${mount_point}/docker" \
    "${mount_point}/models"

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
    ca-certificates \
    certbot \
    curl \
    docker-compose-v2 \
    docker.io \
    git

install -d -m 0755 /etc/docker
cat > /etc/docker/daemon.json <<EOF
{
  "data-root": "${mount_point}/docker",
  "log-driver": "json-file",
  "log-opts": {
    "max-file": "3",
    "max-size": "10m"
  }
}
EOF

systemctl enable docker
systemctl restart docker

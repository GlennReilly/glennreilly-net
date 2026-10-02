#!/usr/bin/env bash
# One-off Droplet setup, equivalent to cloud-init.yaml. Run as root on a fresh Ubuntu 24.04 Droplet.
# Safe to re-run.
#
#   bash server-setup.sh "<deploy public key>" "<acme email>"
set -euo pipefail

DEPLOY_PUBKEY="${1:?usage: server-setup.sh <deploy-public-key> <acme-email>}"
ACME_EMAIL="${2:?usage: server-setup.sh <deploy-public-key> <acme-email>}"

export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get upgrade -yq
apt-get install -yq ca-certificates curl rsync ufw fail2ban unattended-upgrades

# Automatic security updates
printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' \
  > /etc/apt/apt.conf.d/20auto-upgrades

# 2 GB swap
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# Docker
command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh

# deploy user for GitHub Actions
id deploy >/dev/null 2>&1 || useradd -m -s /bin/bash deploy
passwd -l deploy >/dev/null
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
echo "$DEPLOY_PUBKEY" > /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

# App directory + secrets
install -d -o deploy -g deploy /opt/demos
echo "ACME_EMAIL=$ACME_EMAIL" > /opt/demos/.env
chown deploy:deploy /opt/demos/.env
chmod 600 /opt/demos/.env

# SSH: keys only
printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\n' > /etc/ssh/sshd_config.d/10-hardening.conf
systemctl restart ssh

# Firewall
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo
echo "Done. Check:"
id deploy
docker --version
swapon --show
ufw status | head -8

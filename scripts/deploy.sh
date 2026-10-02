#!/usr/bin/env bash
# Runs ON THE DROPLET (as the `deploy` user), invoked over SSH by GitHub Actions.
#
#   deploy.sh            -> pull + (re)start everything, reload Caddy
#   deploy.sh year-round -> pull + restart just that service
set -euo pipefail
cd /opt/demos

target="${1:-all}"
if [[ ! "$target" =~ ^[a-z0-9-]+$ ]]; then
  echo "invalid service name: $target" >&2
  exit 1
fi

if [[ "$target" == "all" ]]; then
  docker compose pull --ignore-pull-failures
  docker compose up -d --remove-orphans
else
  docker compose pull "$target"
  docker compose up -d --no-deps "$target"
fi

# Pick up Caddyfile changes without dropping connections.
docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile

docker image prune -f >/dev/null
docker compose ps

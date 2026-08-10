#!/usr/bin/env bash
# =============================================================================
#  Starts the only container this edition needs: Postgres on 55432.
#
#  Docker lives inside WSL on this machine, so run it from there:
#      wsl -d Ubuntu -u root bash -lc 'cd /mnt/c/Users/nuan/dev/lifeos-mono && bash scripts/infra-up.sh'
#
#  Everything runs under the compose project `lifeos-mono` with its own
#  container name — see scripts/compose-local.yml for why that matters when the
#  microservice edition is also up.
# =============================================================================
set -u
cd "$(dirname "$0")/.." || exit 1

PROJECT="lifeos-mono"
CONTAINER="lifeos-mono-postgres"
COMPOSE=(docker compose -p "$PROJECT" -f docker-compose.yml -f scripts/compose-local.yml)

service docker start >/dev/null 2>&1 || true

[ -f .env ] || cp .env.example .env

# Docker Compose does not strip carriage returns from .env, so a file saved on
# Windows silently turns POSTGRES_PASSWORD into "lifeos_secret\r" — the container
# is created with that password and every client then fails to authenticate.
sed -i 's/\r$//' .env .env.example 2>/dev/null || true

echo "==> starting postgres"
"${COMPOSE[@]}" up -d || exit 1

echo "==> waiting for health"
for i in $(seq 1 24); do
  state=$(docker inspect -f '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo missing)
  if [ "$state" = "healthy" ]; then
    echo "healthy after $((i * 5))s"
    break
  fi
  if [ "$i" = "24" ]; then
    echo "still '$state' after 120s — check: docker logs $CONTAINER"
    exit 1
  fi
  sleep 5
done

"${COMPOSE[@]}" ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'

echo
echo "Postgres is on localhost:55432, database 'lifeos', user 'lifeos'."
echo "Next:  powershell -ExecutionPolicy Bypass -File scripts\\run-local.ps1"

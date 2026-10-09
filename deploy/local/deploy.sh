#!/usr/bin/env bash
# StockSteps local development server (Phase 5B Local): build and run the MOCK backend on a LAN Docker host over SSH.
# Run from the repository root on the development Mac. Usage: deploy/local/deploy.sh <command> [arg]
#
#   deploy            package the Docker build context, upload it as a new release, build stocksteps-local:<tag>, start it
#   start | stop | restart
#   status            container state, health, CPU/memory, published port
#   logs [N]          last N log lines (default 200)
#   health            /health/live, /health/ready and /api/v1/meta from this Mac
#   releases          uploaded releases and stocksteps-local images on the host
#   rollback <tag>    run a previously built image again (no rebuild)
#   smoke             run deploy/local/smoke-test.sh against the server
#   reliability       run deploy/local/reliability-check.sh on the host (restarts the StockSteps container once; MOCK state is lost)
#
# Connection settings come from deploy/local/server.env (git-ignored; copy server.env.example). Only files in the .dockerignore allow-list are
# uploaded; nothing secret is sent. The script only touches ~/stocksteps-local on the host, the Compose project "stocksteps-local" and
# images named stocksteps-local:* — never other containers, networks, volumes or global prunes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/deploy/local"
[[ -f "$HERE/server.env" ]] || { echo "Missing $HERE/server.env (copy server.env.example and fill it in)." >&2; exit 1; }
# shellcheck source=/dev/null
source "$HERE/server.env"
: "${STOCKSTEPS_SSH:?set STOCKSTEPS_SSH (user@host) in server.env}"
: "${STOCKSTEPS_BIND_ADDR:?set STOCKSTEPS_BIND_ADDR (the host LAN IP) in server.env}"
STOCKSTEPS_HOST_PORT="${STOCKSTEPS_HOST_PORT:-8081}"
REMOTE_DIR="${STOCKSTEPS_REMOTE_DIR:-stocksteps-local}"   # relative to the remote home directory
KEEP_RELEASES="${STOCKSTEPS_KEEP_RELEASES:-5}"
BASE_URL="http://$STOCKSTEPS_BIND_ADDR:$STOCKSTEPS_HOST_PORT"

remote() { ssh -o BatchMode=yes "$STOCKSTEPS_SSH" "$@"; }
compose() { remote "cd ~/$REMOTE_DIR && docker compose $*"; }

# Release tag: commit SHA, plus a content hash of uncommitted changes so a dirty tree never reuses a clean tag.
release_tag() {
  local sha; sha="$(git -C "$ROOT" rev-parse --short=12 HEAD)"
  if [[ -n "$(git -C "$ROOT" status --porcelain -- Dockerfile .dockerignore settings.gradle.kts build.gradle.kts gradle.properties gradle core server)" ]]; then
    echo "$sha-dirty-$( (git -C "$ROOT" diff HEAD -- core server; git -C "$ROOT" ls-files --others --exclude-standard -- core server | xargs -I{} cat "$ROOT/{}") | shasum | cut -c1-8)"
  else echo "$sha"; fi
}

write_env() { # $1 tag — the Compose .env on the host holds no secrets
  remote "cat > ~/$REMOTE_DIR/.env" <<EOF
STOCKSTEPS_IMAGE_TAG=$1
STOCKSTEPS_CONTEXT=./releases/$1
STOCKSTEPS_BIND_ADDR=$STOCKSTEPS_BIND_ADDR
STOCKSTEPS_HOST_PORT=$STOCKSTEPS_HOST_PORT
EOF
}

package() { # $1 output tarball — the .dockerignore allow-list, without macOS metadata
  local stage; stage="$(mktemp -d)"
  rsync -a --exclude-from=- "$ROOT/Dockerfile" "$ROOT/.dockerignore" "$ROOT/gradlew" "$ROOT/gradle" "$ROOT/settings.gradle.kts" \
    "$ROOT/build.gradle.kts" "$ROOT/gradle.properties" "$ROOT/core" "$ROOT/server" "$stage/" <<'EOF'
build/
.gradle/
.kotlin/
server/data/
local.properties
.env
.env.*
*.env
*service-account*.json
*serviceAccount*.json
*credentials*.json
*-key.json
google-services.json
GoogleService-Info.plist
*.p12
*.pem
*.key
*.jks
*.keystore
*.log
.DS_Store
*.iml
EOF
  COPYFILE_DISABLE=1 tar --no-xattrs --no-mac-metadata -czf "$1" -C "$stage" .
  rm -rf "$stage"
}

cmd_deploy() {
  local tag tmp tarball; tag="$(release_tag)"; tmp="$(mktemp -d)"; tarball="$tmp/context.tgz"
  echo "Release $tag → $STOCKSTEPS_SSH:~/$REMOTE_DIR/releases/$tag"
  package "$tarball"
  remote "mkdir -p ~/$REMOTE_DIR/releases && chmod 700 ~/$REMOTE_DIR"
  scp -q -o BatchMode=yes "$HERE/docker-compose.yml" "$STOCKSTEPS_SSH:$REMOTE_DIR/docker-compose.yml"
  scp -q -o BatchMode=yes "$tarball" "$STOCKSTEPS_SSH:$REMOTE_DIR/releases/$tag.tgz"
  rm -rf "$tmp"
  remote "set -e; cd ~/$REMOTE_DIR/releases; rm -rf -- '$tag'; mkdir '$tag'; tar xzf '$tag.tgz' -C '$tag' 2>/dev/null; rm '$tag.tgz'"
  write_env "$tag"
  echo "Building stocksteps-local:$tag for linux/amd64 (cold builds take ~10 min)…"
  compose build api
  compose up -d --wait --wait-timeout 180 api
  # Keep the newest releases' sources; their images stay available for rollback until removed explicitly.
  remote "cd ~/$REMOTE_DIR/releases && ls -1t | tail -n +$((KEEP_RELEASES + 1)) | xargs -r rm -rf --"
  cmd_health
}

cmd_health() {
  for path in /health/live /health/ready /api/v1/meta; do
    printf '%-16s ' "$path"; curl -sS --max-time 5 -o /dev/null -w '%{http_code}\n' "$BASE_URL$path" || echo "unreachable"
  done
  curl -sS --max-time 5 "$BASE_URL/api/v1/meta" | tr -d ' \n'; echo
}

cmd_status() {
  compose ps
  remote "docker inspect -f 'health={{.State.Health.Status}} restarts={{.RestartCount}} started={{.State.StartedAt}} user={{.Config.User}} readonly={{.HostConfig.ReadonlyRootfs}}' stocksteps-local-api-1; docker stats --no-stream --format 'cpu={{.CPUPerc}} mem={{.MemUsage}} pids={{.PIDs}}' stocksteps-local-api-1; cat ~/$REMOTE_DIR/.env | grep TAG"
}

case "${1:-}" in
  deploy) cmd_deploy ;;
  start) compose up -d --no-build --wait api ;;
  stop) compose stop api ;;
  restart) compose restart api ;;
  status) cmd_status ;;
  logs) compose logs --no-color --tail "${2:-200}" api ;;
  health) cmd_health ;;
  releases) remote "ls -1t ~/$REMOTE_DIR/releases; docker images stocksteps-local --format '{{.Tag}} {{.CreatedSince}} {{.Size}}'" ;;
  rollback)
    tag="${2:?usage: rollback <tag> (see: releases)}"
    remote "docker image inspect stocksteps-local:$tag >/dev/null" || { echo "No image stocksteps-local:$tag on the host." >&2; exit 1; }
    remote "test -d ~/$REMOTE_DIR/releases/$tag" || remote "mkdir -p ~/$REMOTE_DIR/releases/$tag"   # build context only needed for rebuilds
    write_env "$tag"; compose up -d --no-build --wait api; cmd_health ;;
  smoke) STOCKSTEPS_BASE_URL="$BASE_URL" "$HERE/smoke-test.sh" ;;
  reliability)
    scp -q -o BatchMode=yes "$HERE/reliability-check.sh" "$STOCKSTEPS_SSH:$REMOTE_DIR/reliability-check.sh"
    remote "chmod 700 ~/$REMOTE_DIR/reliability-check.sh && ~/$REMOTE_DIR/reliability-check.sh" ;;
  *) sed -n '2,16p' "$0"; exit 1 ;;
esac

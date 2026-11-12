#!/usr/bin/env bash
#
# Deploys one service of the FlashDrop stack to the VPS.
#
#   1. pins the working tree to the SHA that was tested in CI
#   2. applies the migrations the deploy owns (orders runs with Flyway off)
#   3. rebuilds and recreates the container
#   4. gates on the health URL and on the state of the schema
#   5. rolls back to the last good SHA when a gate fails
#
# Exit code 0: the new SHA is running and healthy.
# Exit code 1: the deploy failed. The previous version was restored when it
#              was known.
#
# The migrations are applied before the new version starts and are never
# reverted: they are additive by construction, so the previous version keeps
# working against the new schema.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

SERVICE=""
COMPOSE_SERVICE=""
HEALTH_URL=""
TARGET_SHA=""
PREVIOUS_SHA_ARG=""
MIGRATION_DIR_ARG=""
MIGRATION_SCHEMA_ARG=""

while [ $# -gt 0 ]; do
  case "$1" in
    --service) SERVICE="$2"; shift 2 ;;
    --compose-service) COMPOSE_SERVICE="$2"; shift 2 ;;
    --health-url) HEALTH_URL="$2"; shift 2 ;;
    --sha) TARGET_SHA="$2"; shift 2 ;;
    --previous-sha) PREVIOUS_SHA_ARG="$2"; shift 2 ;;
    --migration-dir) MIGRATION_DIR_ARG="$2"; shift 2 ;;
    --migration-schema) MIGRATION_SCHEMA_ARG="$2"; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done

[ -n "$SERVICE" ] || fail "--service is required"
[ -n "$HEALTH_URL" ] || fail "--health-url is required"
[ -n "$TARGET_SHA" ] || fail "--sha is required"

service_config "$SERVICE"

if [ -n "$COMPOSE_SERVICE" ] && [ -n "$SERVICE_COMPOSE" ] && [ "$COMPOSE_SERVICE" != "$SERVICE_COMPOSE" ]; then
  fail "compose service mismatch for '$SERVICE': configured '$SERVICE_COMPOSE', got '$COMPOSE_SERVICE'"
fi
# A service without a row in the configuration (the gateway has no database)
# takes the compose service from the caller.
[ -n "$SERVICE_COMPOSE" ] || SERVICE_COMPOSE="$COMPOSE_SERVICE"
[ -n "$SERVICE_COMPOSE" ] || fail "service '$SERVICE' is not configured in $CONFIG_FILE"

MIGRATION_DIR="${MIGRATION_DIR_ARG:-$SERVICE_MIGRATION_DIR}"
FLYWAY_SCHEMA="${MIGRATION_SCHEMA_ARG:-$SERVICE_FLYWAY_SCHEMA}"

STATE_FILE="$STATE_DIR/$SERVICE.sha"

wait_for_health() {
  local attempt
  for attempt in $(seq 1 "$HEALTH_ATTEMPTS"); do
    if curl -sf --max-time 5 "$HEALTH_URL" >/dev/null 2>&1; then
      log "health check passed: $HEALTH_URL"
      return 0
    fi
    log "waiting for $HEALTH_URL ($attempt/$HEALTH_ATTEMPTS)"
    sleep "$HEALTH_INTERVAL"
  done
  return 1
}

rollback() {
  if [ -z "$PREVIOUS_SHA" ]; then
    log "ROLLBACK SKIPPED: there is no known previous version of $SERVICE"
    log "the failed version is still running: fix it by hand"
    return 1
  fi

  log "rolling $SERVICE back to $PREVIOUS_SHA"
  git checkout --force --detach "$PREVIOUS_SHA" || return 1
  compose up -d --no-deps --build "$SERVICE_COMPOSE" || return 1
  if ! wait_for_health; then
    log "ROLLBACK FAILED: $PREVIOUS_SHA did not become healthy"
    return 1
  fi
  log "rolled $SERVICE back to $PREVIOUS_SHA"
  return 0
}

current_sha="$(git rev-parse HEAD)"
if [ "$current_sha" != "$TARGET_SHA" ]; then
  log "the working tree is at $current_sha, moving it to $TARGET_SHA"
  git checkout --force --detach "$TARGET_SHA"
fi

PREVIOUS_SHA=""
if [ -f "$STATE_FILE" ]; then
  PREVIOUS_SHA="$(cat "$STATE_FILE")"
fi
if [ -z "$PREVIOUS_SHA" ]; then
  PREVIOUS_SHA="$PREVIOUS_SHA_ARG"
fi
# The previous version is only useful when it is a different commit.
if [ "$PREVIOUS_SHA" = "$TARGET_SHA" ]; then
  PREVIOUS_SHA=""
fi

if [ -n "$MIGRATION_DIR" ]; then
  case "$MIGRATION_DIR" in
    /*|[A-Za-z]:/*|~/*) ;;
    *) MIGRATION_DIR="$REPO_ROOT/$MIGRATION_DIR" ;;
  esac
fi

if [ "$MIGRATION_OWNER" = "cd" ] && [ -n "$MIGRATION_DIR" ]; then
  log "applying the migrations $SERVICE owns before starting the new version"
  if ! "$SCRIPT_DIR/migrate.sh" apply --service "$SERVICE" \
      --migration-dir "$MIGRATION_DIR" --migration-schema "$FLYWAY_SCHEMA"; then
    fail "the migrations of $SERVICE failed; the running version was left untouched"
  fi
fi

if [ -n "$SERVICE_PRE_DEPLOY" ]; then
  log "running the pre-deploy step of $SERVICE: $SERVICE_PRE_DEPLOY"
  if ! ( cd "$REPO_ROOT" && bash -c "$SERVICE_PRE_DEPLOY" ); then
    fail "the pre-deploy step of $SERVICE failed"
  fi
fi

log "rebuilding $SERVICE ($SERVICE_COMPOSE)"
if ! compose up -d --no-deps --build "$SERVICE_COMPOSE"; then
  log "the container of $SERVICE could not be started"
  rollback || true
  exit 1
fi

if ! wait_for_health; then
  log "the health check of $SERVICE timed out"
  rollback || true
  exit 1
fi

if [ -n "$MIGRATION_DIR" ]; then
  if ! "$SCRIPT_DIR/migrate.sh" verify --service "$SERVICE" \
      --migration-dir "$MIGRATION_DIR" --migration-schema "$FLYWAY_SCHEMA"; then
    log "the schema of $SERVICE is not in the expected state"
    rollback || true
    exit 1
  fi
fi

mkdir -p "$STATE_DIR"
printf '%s\n' "$TARGET_SHA" > "$STATE_FILE"
log "deployed $TARGET_SHA for $SERVICE"

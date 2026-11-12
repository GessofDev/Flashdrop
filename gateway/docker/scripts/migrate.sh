#!/usr/bin/env bash
#
# Applies or verifies the database migrations of one service on the VPS.
#
#   migrate.sh apply  --service orders
#   migrate.sh verify --service orders
#
# apply   runs the Flyway CLI (the same version the services use) against the
#         service database. It is used for the services whose own application
#         runs with Flyway disabled, so nothing else would ever apply their
#         migrations.
#
# verify  compares the migration files in the repository with the versions
#         recorded in the database and fails when one of them is missing. It
#         runs after the new version is healthy, which is what turns a missing
#         migration into a failed deploy instead of a broken service.
#
# The Flyway CLI baselines an existing schema at version 1 and applies
# everything after it. Every migration in this repository is written
# idempotently, so applying them twice is harmless.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

MODE=""
SERVICE=""
MIGRATION_DIR_ARG=""
MIGRATION_SCHEMA_ARG=""

while [ $# -gt 0 ]; do
  case "$1" in
    apply|verify) MODE="$1"; shift ;;
    --service) SERVICE="$2"; shift 2 ;;
    --migration-dir) MIGRATION_DIR_ARG="$2"; shift 2 ;;
    --migration-schema) MIGRATION_SCHEMA_ARG="$2"; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done

[ -n "$MODE" ] || fail "usage: migrate.sh <apply|verify> --service <id>"
[ -n "$SERVICE" ] || fail "--service is required"

service_config "$SERVICE"

MIGRATION_DIR="${MIGRATION_DIR_ARG:-$SERVICE_MIGRATION_DIR}"
FLYWAY_SCHEMA="${MIGRATION_SCHEMA_ARG:-$SERVICE_FLYWAY_SCHEMA}"
if [ -n "$MIGRATION_DIR" ]; then
  case "$MIGRATION_DIR" in
    /*|[A-Za-z]:/*|~/*) ;;
    *) MIGRATION_DIR="$REPO_ROOT/$MIGRATION_DIR" ;;
  esac
fi

[ -n "$MIGRATION_DIR" ] || fail "service '$SERVICE' has no migration directory configured"

db_credentials

case "$MODE" in
  apply)
    log "applying the migrations of $SERVICE (schema '${FLYWAY_SCHEMA:-<default>}')"
    log "  database: $DB_URL as $DB_USER"

    docker pull "$FLYWAY_IMAGE" >/dev/null

    # The baseline options are command line flags on purpose: Flyway 9 ignores
    # the FLYWAY_BASELINEONMIGRATE environment variable (it only understands
    # the FLYWAY_BASELINE_ON_MIGRATE form), and an ignored variable fails
    # silently on a schema that already exists.
    flyway_cmd=(
      docker run --rm --network "$FLOCI_NETWORK"
      -v "$MIGRATION_DIR:/migrations:ro"
      -e "FLYWAY_URL=$DB_URL"
      -e "FLYWAY_USER=$DB_USER"
      -e "FLYWAY_PASSWORD=$DB_PASS"
      -e "FLYWAY_CONNECTRETRIES=3"
      "$FLYWAY_IMAGE"
      -locations=filesystem:/migrations
      -baselineOnMigrate=true
      -baselineVersion=1
    )
    if [ -n "$FLYWAY_SCHEMA" ]; then
      flyway_cmd+=("-schemas=$FLYWAY_SCHEMA")
    fi
    flyway_cmd+=(migrate)

    "${flyway_cmd[@]}" || fail "the migrations of $SERVICE could not be applied"

    log "migrations of $SERVICE applied"
    ;;

  verify)
    expected="$(migration_versions "$MIGRATION_DIR")"
    applied="$(applied_versions "$FLYWAY_SCHEMA")"

    missing=""
    while read -r version file; do
      [ -n "$version" ] || continue
      if ! printf '%s\n' "$applied" | grep -qx "$version"; then
        missing="$missing $file"
      fi
    done <<EOF
$expected
EOF

    if [ -n "$missing" ]; then
      fail "the database of $SERVICE is missing the migrations:$missing"
    fi

    log "all the migrations of $SERVICE are applied"
    ;;

  *)
    fail "unknown mode: $MODE"
    ;;
esac

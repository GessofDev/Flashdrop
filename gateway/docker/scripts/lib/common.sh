#!/usr/bin/env bash
#
# Shared helpers for the CD deploy scripts (deploy-service.sh, migrate.sh).
# Sourced, never executed directly.
#
# Every value that differs per service lives in deploy-services.conf so the
# scripts themselves stay generic.

# <repo>/gateway/docker/scripts/lib -> <repo>
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"

COMPOSE_DIR="$REPO_ROOT/gateway/docker"
COMPOSE_FILE="${COMPOSE_FILE:-$COMPOSE_DIR/docker-compose.stack.yml}"
ENV_FILE="${ENV_FILE:-$COMPOSE_DIR/.env}"
CONFIG_FILE="${DEPLOY_SERVICES_CONF:-$REPO_ROOT/gateway/docker/scripts/deploy-services.conf}"
# The state directory holds the SHA of the last good version of every service.
# shellcheck disable=SC2034  # read by deploy-service.sh
STATE_DIR="${DEPLOY_STATE_DIR:-$HOME/.flashdrop-deploy}"

# Pinned images. The Flyway version matches the one the services use
# (Spring Boot 3.2.5 / 3.3.5 -> Flyway 9.22.3) so the schema history written
# here is the same one the application reads.
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:9.22.3-alpine}"
PSQL_IMAGE="${PSQL_IMAGE:-postgres:15-alpine}"

# The databases live behind the Floci proxy and are only reachable from the
# floci_default network.
FLOCI_NETWORK="${FLOCI_NETWORK:-floci_default}"

HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-24}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

log() { printf '[deploy] %s\n' "$*"; }
fail() { printf '[deploy] ERROR: %s\n' "$*" >&2; exit 1; }

# service_config <id>
#
# Fills SERVICE_COMPOSE, SERVICE_CONTAINER, MIGRATION_OWNER, DB_KIND,
# DB_USER_ENV, DB_PASS_ENV, SERVICE_MIGRATION_DIR and SERVICE_FLYWAY_SCHEMA
# from deploy-services.conf. Unknown services are allowed: the gateway has no
# database and therefore no row.
service_config() {
  local id="$1" line
  SERVICE_COMPOSE=""
  SERVICE_CONTAINER=""
  MIGRATION_OWNER=""
  DB_KIND=""
  DB_USER_ENV=""
  DB_PASS_ENV=""
  SERVICE_MIGRATION_DIR=""
  SERVICE_FLYWAY_SCHEMA=""
  SERVICE_PRE_DEPLOY=""

  [ -f "$CONFIG_FILE" ] || fail "service configuration not found: $CONFIG_FILE"
  line="$(awk -F'|' -v id="$id" '$1 == id { print; exit }' "$CONFIG_FILE")"
  [ -n "$line" ] || return 0

  # shellcheck disable=SC2034  # MIGRATION_OWNER, SERVICE_MIGRATION_DIR,
  # SERVICE_FLYWAY_SCHEMA and SERVICE_PRE_DEPLOY are read by deploy-service.sh
  # and migrate.sh.
  IFS='|' read -r _ SERVICE_COMPOSE SERVICE_CONTAINER MIGRATION_OWNER DB_KIND \
    DB_USER_ENV DB_PASS_ENV SERVICE_MIGRATION_DIR SERVICE_FLYWAY_SCHEMA \
    SERVICE_PRE_DEPLOY <<< "$line"
}

# compose <args...> — runs docker compose against the stack file.
compose() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

# service_env_dump — prints the environment the service runs with.
#
# The running container is the cheapest source. When the service was never
# deployed, a throwaway container resolves the same compose environment.
service_env_dump() {
  local dumped
  if [ -n "$SERVICE_CONTAINER" ] && docker inspect "$SERVICE_CONTAINER" >/dev/null 2>&1; then
    dumped="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$SERVICE_CONTAINER")"
  else
    dumped="$(compose run --rm --no-deps --entrypoint env "$SERVICE_COMPOSE" 2>/dev/null)"
  fi
  printf '%s\n' "$dumped" | grep -E '^[A-Za-z_][A-Za-z0-9_]*=' || true
}

# db_credentials — sets DB_URL, DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASS
# for the configured service.
db_credentials() {
  local env_dump host port name

  env_dump="$(service_env_dump)"

  case "$DB_KIND" in
    jdbc)
      DB_URL="$(printf '%s\n' "$env_dump" | sed -n 's/^DB_URL=//p' | head -n 1)"
      DB_USER="$(printf '%s\n' "$env_dump" | sed -n "s/^${DB_USER_ENV}=//p" | head -n 1)"
      DB_PASS="$(printf '%s\n' "$env_dump" | sed -n "s/^${DB_PASS_ENV}=//p" | head -n 1)"
      ;;
    parts)
      host="$(printf '%s\n' "$env_dump" | sed -n 's/^DELIVERY_DB_HOST=//p' | head -n 1)"
      port="$(printf '%s\n' "$env_dump" | sed -n 's/^DELIVERY_DB_PORT=//p' | head -n 1)"
      name="$(printf '%s\n' "$env_dump" | sed -n 's/^DELIVERY_DB_NAME=//p' | head -n 1)"
      DB_URL="jdbc:postgresql://${host}:${port}/${name}"
      DB_USER="$(printf '%s\n' "$env_dump" | sed -n "s/^${DB_USER_ENV}=//p" | head -n 1)"
      DB_PASS="$(printf '%s\n' "$env_dump" | sed -n "s/^${DB_PASS_ENV}=//p" | head -n 1)"
      ;;
    *)
      fail "service '$SERVICE' has no database configuration"
      ;;
  esac

  [ -n "$DB_URL" ] || fail "could not resolve the database URL for '$SERVICE'"
  [ -n "$DB_USER" ] || fail "could not resolve the database user for '$SERVICE'"

  # The Flyway CLI expects a JDBC URL (Flyway 9 does not understand the short
  # postgresql:// form), so DB_URL keeps the jdbc: prefix. psql needs the
  # plain coordinates, which are read from the same URL.
  local plain rest hostport
  plain="${DB_URL#jdbc:}"
  rest="${plain#*://}"
  hostport="${rest%%/*}"
  DB_NAME="${rest#*/}"
  DB_NAME="${DB_NAME%%\?*}"
  DB_HOST="${hostport%%:*}"
  if [ "$DB_HOST" = "$hostport" ]; then
    DB_PORT="5432"
  else
    DB_PORT="${hostport##*:}"
  fi
}

# migration_versions <dir> — prints "<version> <file name>" per migration file.
migration_versions() {
  local dir="$1" file version
  [ -d "$dir" ] || fail "migration directory not found: $dir"
  for file in "$dir"/V*__*.sql; do
    [ -e "$file" ] || continue
    version="$(basename "$file" | sed -n 's/^V\([0-9][0-9]*\)__.*$/\1/p')"
    [ -n "$version" ] || fail "cannot parse a version from $(basename "$file")"
    printf '%s %s\n' "$version" "$(basename "$file")"
  done
}

# applied_versions <schema> — prints the versions recorded as applied in the
# Flyway history of the given schema (empty schema means the default one).
applied_versions() {
  local schema="$1" sql out rc
  if [ -n "$schema" ]; then
    sql="SELECT version FROM \"${schema}\".flyway_schema_history WHERE success ORDER BY installed_rank;"
  else
    sql="SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank;"
  fi

  if out="$(docker run --rm --network "$FLOCI_NETWORK" -e PGPASSWORD="$DB_PASS" \
    "$PSQL_IMAGE" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 -Atc "$sql" 2>&1)"; then
    rc=0
  else
    rc=$?
  fi

  if [ "$rc" -ne 0 ]; then
    if printf '%s' "$out" | grep -qi 'does not exist'; then
      # stderr: the caller captures stdout, and only the versions belong there.
      log "no flyway_schema_history found in schema '${schema:-<default>}'" >&2
      return 0
    fi
    fail "cannot read the Flyway history: ${out}"
  fi

  printf '%s\n' "$out" | grep -E '^[0-9]+$' || true
}

#!/usr/bin/env bash
#
# Tests for the CD deploy scripts.
#
# No Docker, no database, no network: git, docker and curl are replaced by
# fakes that record what the scripts try to do, so the deploy logic can be
# checked on any machine.
#
#   bash gateway/docker/scripts/tests/run-tests.sh
set -uo pipefail

TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$(cd "$TESTS_DIR/.." && pwd)"

WORK_DIR="$(mktemp -d)"
if [ -n "${KEEP_WORK_DIR:-}" ]; then
  printf 'work dir: %s\n' "$WORK_DIR"
else
  trap 'rm -rf "$WORK_DIR"' EXIT
fi
BIN_DIR="$WORK_DIR/bin"
mkdir -p "$BIN_DIR"

LOG="$WORK_DIR/calls.log"
GIT_STATE="$WORK_DIR/git-head"
STATE_DIR="$WORK_DIR/state"
OUT="$WORK_DIR/output.log"
mkdir -p "$STATE_DIR"

ORDERS_SHA="1111111111111111111111111111111111111111"
PREVIOUS_SHA="2222222222222222222222222222222222222222"

ORDERS_ENV="DB_URL=jdbc:postgresql://172.16.1.2:7002/flashdrop_orders
DB_USERNAME=orders_app
DB_PASSWORD=secret"

ALL_MIGRATIONS=""
MISSING_V4=""
MIGRATION_DIR="$SCRIPTS_DIR/../../../services/orders-service/src/main/resources/db/migration"

# The applied versions are derived from the repository, so the fakes stay in
# sync when a migration is added.
if [ ! -d "$MIGRATION_DIR" ]; then
  printf 'the orders migration directory is missing: %s\n' "$MIGRATION_DIR" >&2
  exit 1
fi
all_versions="$(ls "$MIGRATION_DIR" | sed -n 's/^V\([0-9][0-9]*\)__.*$/\1/p' | sort -n)"
ALL_MIGRATIONS="$(printf '%s\n' "$all_versions" | tr '\n' ' ')"
ALL_MIGRATIONS="${ALL_MIGRATIONS% }"
v4_version="$(ls "$MIGRATION_DIR" | sed -n 's/^V\([0-9][0-9]*\)__order_delivery_operations\.sql$/\1/p')"
MISSING_V4="$(printf '%s\n' "$all_versions" | grep -vx "$v4_version" | tr '\n' ' ')"
MISSING_V4="${MISSING_V4% }"

PASSED=0
FAILED=0

pass() { PASSED=$((PASSED + 1)); printf '  ok   %s\n' "$1"; }
failure() { FAILED=$((FAILED + 1)); printf '  FAIL %s\n' "$1"; }

assert_contains() { # <description> <file> <needle>
  if grep -qF -- "$3" "$2"; then pass "$1"; else failure "$1 (not found: $3)"; fi
}

assert_not_contains() { # <description> <file> <needle>
  if grep -qF -- "$3" "$2"; then failure "$1 (unexpectedly found: $3)"; else pass "$1"; fi
}

assert_equals() { # <description> <expected> <actual>
  if [ "$2" = "$3" ]; then pass "$1"; else failure "$1 (expected '$2', got '$3')"; fi
}

assert_order() { # <description> <file> <first needle> <second needle>
  local first_line second_line
  first_line="$(grep -nF -- "$3" "$2" | head -n 1 | cut -d: -f1)"
  second_line="$(grep -nF -- "$4" "$2" | head -n 1 | cut -d: -f1)"
  if [ -n "$first_line" ] && [ -n "$second_line" ] && [ "$first_line" -lt "$second_line" ]; then
    pass "$1"
  else
    failure "$1 ('$3' at ${first_line:-none} must come before '$4' at ${second_line:-none})"
  fi
}

write_fakes() {
  cat > "$BIN_DIR/git" <<'FAKE'
#!/usr/bin/env bash
echo "git $*" >> "$FAKE_LOG"
case "$1" in
  rev-parse) cat "$FAKE_GIT_STATE" ;;
  checkout)
    sha=""
    for arg in "$@"; do
      case "$arg" in
        -*) ;;
        *) sha="$arg" ;;
      esac
    done
    printf '%s\n' "$sha" > "$FAKE_GIT_STATE"
    ;;
esac
exit 0
FAKE

  cat > "$BIN_DIR/docker" <<'FAKE'
#!/usr/bin/env bash
echo "docker $*" >> "$FAKE_LOG"
case "$1" in
  run)
    if printf '%s' "$*" | grep -q 'psql'; then
      if [ "${FAKE_PSQL_RC:-0}" != "0" ]; then
        echo 'ERROR:  relation "flyway_schema_history" does not exist' >&2
        exit 1
      fi
      printf '%s\n' ${FAKE_PSQL_OUT:-} | grep -E '^[0-9]+$' || true
      exit 0
    fi
    exit "${FAKE_FLYWAY_RC:-0}"
    ;;
  inspect)
    [ "${FAKE_CONTAINER_EXISTS:-1}" = "1" ] || exit 1
    printf '%s\n' "${FAKE_SERVICE_ENV:-}"
    exit 0
    ;;
  compose)
    if printf '%s' "$*" | grep -q -- '--entrypoint env'; then
      printf '%s\n' "${FAKE_SERVICE_ENV:-}"
      exit 0
    fi
    exit "${FAKE_COMPOSE_RC:-0}"
    ;;
  pull)
    exit 0
    ;;
esac
exit 0
FAKE

  cat > "$BIN_DIR/curl" <<'FAKE'
#!/usr/bin/env bash
echo "curl $*" >> "$FAKE_LOG"
exit "${FAKE_CURL_RC:-0}"
FAKE

  chmod +x "$BIN_DIR/git" "$BIN_DIR/docker" "$BIN_DIR/curl"
}

# run_deploy <args...> — runs deploy-service.sh against the fakes.
run_deploy() {
  : > "$LOG"
  : > "$OUT"
  PATH="$BIN_DIR:$PATH" \
    FAKE_LOG="$LOG" \
    FAKE_GIT_STATE="$GIT_STATE" \
    FAKE_SERVICE_ENV="$ORDERS_ENV" \
    FAKE_PSQL_OUT="$FAKE_PSQL_OUT" \
    FAKE_CURL_RC="$FAKE_CURL_RC" \
    FAKE_FLYWAY_RC="$FAKE_FLYWAY_RC" \
    FAKE_COMPOSE_RC="$FAKE_COMPOSE_RC" \
    DEPLOY_STATE_DIR="$STATE_DIR" \
    HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-1}" \
    HEALTH_INTERVAL=0 \
    bash "$SCRIPTS_DIR/deploy-service.sh" "$@" > "$OUT" 2>&1
}

# run_migrate <args...> — runs migrate.sh against the fakes.
run_migrate() {
  : > "$LOG"
  : > "$OUT"
  PATH="$BIN_DIR:$PATH" \
    FAKE_LOG="$LOG" \
    FAKE_GIT_STATE="$GIT_STATE" \
    FAKE_SERVICE_ENV="$ORDERS_ENV" \
    FAKE_PSQL_OUT="$FAKE_PSQL_OUT" \
    FAKE_PSQL_RC="${FAKE_PSQL_RC:-0}" \
    DEPLOY_STATE_DIR="$STATE_DIR" \
    bash "$SCRIPTS_DIR/migrate.sh" "$@" > "$OUT" 2>&1
}

write_fakes

echo "deploy: a healthy service is deployed, pinned to the tested SHA"
FAKE_PSQL_OUT="$ALL_MIGRATIONS"; FAKE_CURL_RC=0; FAKE_FLYWAY_RC=0; FAKE_COMPOSE_RC=0
printf '%s\n' "$PREVIOUS_SHA" > "$GIT_STATE"
rm -f "$STATE_DIR/orders.sha"
run_deploy --service orders --compose-service orders-service \
  --health-url http://127.0.0.1:8083/actuator/health --sha "$ORDERS_SHA"
assert_equals "the deploy succeeds" "0" "$?"
assert_contains "the working tree moves to the tested SHA" "$LOG" "checkout --force --detach $ORDERS_SHA"
assert_not_contains "the deploy never touches origin/main" "$LOG" "origin/main"
assert_contains "the container is rebuilt" "$LOG" "up -d --no-deps --build orders-service"
assert_contains "the migrations are applied with the Flyway CLI" "$LOG" "flyway/flyway:9.22.3-alpine"
assert_contains "the Flyway CLI gets a JDBC URL" "$LOG" "FLYWAY_URL=jdbc:postgresql://"
assert_contains "the baseline is enabled with the CLI flag" "$LOG" "-baselineOnMigrate=true"
assert_contains "the baseline version is the CLI flag" "$LOG" "-baselineVersion=1"
assert_order "the migrations run before the container starts" "$LOG" "flyway/flyway" "up -d --no-deps --build"
assert_equals "the deployed SHA is recorded" "$ORDERS_SHA" "$(cat "$STATE_DIR/orders.sha" 2>/dev/null)"

echo "deploy: a service that never becomes healthy rolls back to the previous SHA"
FAKE_PSQL_OUT="$ALL_MIGRATIONS"; FAKE_CURL_RC=1; FAKE_FLYWAY_RC=0; FAKE_COMPOSE_RC=0
printf '%s\n' "$PREVIOUS_SHA" > "$GIT_STATE"
printf '%s\n' "$PREVIOUS_SHA" > "$STATE_DIR/orders.sha"
run_deploy --service orders --compose-service orders-service \
  --health-url http://127.0.0.1:8083/actuator/health --sha "$ORDERS_SHA"
if [ "$?" -ne 0 ]; then pass "the deploy fails"; else failure "the deploy should fail"; fi
assert_contains "the previous SHA is checked out again" "$LOG" "checkout --force --detach $PREVIOUS_SHA"
assert_contains "the rollback is reported" "$OUT" "rolling orders back to $PREVIOUS_SHA"
assert_equals "the previous SHA stays the recorded one" "$PREVIOUS_SHA" "$(cat "$STATE_DIR/orders.sha")"

echo "deploy: a missing migration fails the deploy and rolls back"
FAKE_PSQL_OUT="$MISSING_V4"; FAKE_CURL_RC=0; FAKE_FLYWAY_RC=0; FAKE_COMPOSE_RC=0
printf '%s\n' "$PREVIOUS_SHA" > "$GIT_STATE"
printf '%s\n' "$PREVIOUS_SHA" > "$STATE_DIR/orders.sha"
run_deploy --service orders --compose-service orders-service \
  --health-url http://127.0.0.1:8083/actuator/health --sha "$ORDERS_SHA"
if [ "$?" -ne 0 ]; then pass "the deploy fails"; else failure "the deploy should fail"; fi
assert_contains "the missing migration is named" "$OUT" "V4__order_delivery_operations.sql"
assert_contains "the previous SHA is checked out again" "$LOG" "checkout --force --detach $PREVIOUS_SHA"

echo "deploy: a failing migration leaves the running version untouched"
FAKE_PSQL_OUT="$ALL_MIGRATIONS"; FAKE_CURL_RC=0; FAKE_FLYWAY_RC=1; FAKE_COMPOSE_RC=0
printf '%s\n' "$PREVIOUS_SHA" > "$GIT_STATE"
rm -f "$STATE_DIR/orders.sha"
run_deploy --service orders --compose-service orders-service \
  --health-url http://127.0.0.1:8083/actuator/health --sha "$ORDERS_SHA"
if [ "$?" -ne 0 ]; then pass "the deploy fails"; else failure "the deploy should fail"; fi
assert_not_contains "the container is not rebuilt" "$LOG" "up -d --no-deps --build orders-service"
assert_not_contains "no rollback is attempted" "$LOG" "checkout --force --detach $PREVIOUS_SHA"

echo "migrate: verify accepts a database that has every migration"
FAKE_PSQL_OUT="$ALL_MIGRATIONS"
run_migrate verify --service orders
assert_equals "the verification succeeds" "0" "$?"

echo "migrate: verify rejects a database that is missing a migration"
FAKE_PSQL_OUT="$MISSING_V4"
run_migrate verify --service orders
if [ "$?" -ne 0 ]; then pass "the verification fails"; else failure "the verification should fail"; fi
assert_contains "the missing migration is named" "$OUT" "V4__order_delivery_operations.sql"

echo "migrate: verify reports a database without a Flyway history"
FAKE_PSQL_OUT=""
FAKE_PSQL_RC=1
run_migrate verify --service orders
if [ "$?" -ne 0 ]; then pass "the verification fails"; else failure "the verification should fail"; fi
assert_contains "the missing history is reported" "$OUT" "flyway_schema_history"

printf '\n%s passed, %s failed\n' "$PASSED" "$FAILED"
if [ "$FAILED" -ne 0 ]; then
  printf '\n--- last output ---\n'
  cat "$OUT"
fi
[ "$FAILED" -eq 0 ]

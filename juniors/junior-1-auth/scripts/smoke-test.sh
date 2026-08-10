#!/usr/bin/env bash
# Smoke test de auth-service. Requiere el servicio corriendo (por defecto en :8081).
#   BASE=http://localhost:8081 ./scripts/smoke-test.sh
set -u
BASE="${BASE:-http://localhost:8081}"
EMAIL="smoke_$(date +%s)@flashdrop.cl"
PASS="Segura1234"
JSON='Content-Type: application/json'

echo "== 1. register (espera 201) =="
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "$BASE/auth/register" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"name\":\"Smoke\",\"lastName\":\"Test\",\"phone\":\"+56911112222\"}"

echo "== 2. login (espera 200 + tokens) =="
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H "$JSON" -d "{\"login\":\"$EMAIL\",\"password\":\"$PASS\"}")
echo "$LOGIN"
ACCESS=$(echo "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
REFRESH=$(echo "$LOGIN" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')

echo "== 3. profile con el access token =="
curl -s "$BASE/auth/profile" -H "Authorization: Bearer $ACCESS"; echo

echo "== 4. refresh (rota el refresh token) =="
curl -s -X POST "$BASE/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$REFRESH\"}"; echo

echo "== 5. jwks (clave pública para el gateway) =="
curl -s "$BASE/auth/.well-known/jwks.json"; echo

echo "== 6. login incorrecto (espera 401 AUTH_INVALID_CREDENTIALS) =="
curl -s -X POST "$BASE/auth/login" -H "$JSON" -d "{\"login\":\"$EMAIL\",\"password\":\"malaClave123\"}"; echo

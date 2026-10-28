#!/usr/bin/env bash
# Crea un usuario de QA con los roles pedidos y deja el token listo para usar.
#
# El registro siempre nace con el rol base "Cliente" (RegisterUserService).
# Este script lo reemplaza por el conjunto pedido usando el endpoint interno
# de asignacion de roles, que es el mismo que consumira el panel de
# administracion cuando exista.
#
#   ./scripts/qa-create-user.sh <email> <password> <rol> [rol...]
#
# Roles validos: Cliente, Restaurante, Repartidor
#
# Para dejar a un usuario SIN roles, usá el endpoint directo:
#   curl -X PUT "$BASE/api/internal/users/<id>/roles" \
#     -H "X-Internal-Api-Key: $INTERNAL_API_KEY" -d '{"roles":[]}'
#
# Variables de entorno:
#   BASE             URL base del servicio (default http://localhost:8081)
#   INTERNAL_API_KEY  clave interna; la misma que usa el servicio para
#                     /api/internal/** (sin ella el endpoint responde 403)
set -uo pipefail

BASE="${BASE:-http://localhost:8081}"
JSON='Content-Type: application/json'

if [ "$#" -lt 3 ]; then
  echo "Uso: $0 <email> <password> <rol> [rol...]" >&2
  echo "Roles validos: Cliente, Restaurante, Repartidor" >&2
  exit 64
fi

EMAIL="$1"
PASS="$2"
shift 2
ROLES=("$@")

if [ -z "${INTERNAL_API_KEY:-}" ]; then
  echo "Falta INTERNAL_API_KEY: es la clave que el servicio exige en /api/internal/**." >&2
  exit 64
fi

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

# Escapa un valor para poder meterlo dentro de un string JSON: sin esto una
# contrasena con comilla, barra invertida, tabulador o salto de linea produce
# un cuerpo invalido y el error que ve QA es "El registro fallo" sin causa.
json_string() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' -e 's/\r/\\r/g' \
    | awk 'BEGIN { ORS = "" } { if (NR > 1) printf "\\n"; print }'
}

# Ejecuta la peticion: deja el cuerpo en $BODY_FILE y devuelve el status HTTP.
# El resultado se decide por el status, no por la forma del cuerpo: un 4xx o
# 5xx con cuerpo raro no puede pasar por exito.
request() {
  local method="$1" url="$2" body="$3"
  shift 3
  curl -sS -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$url" -H "$JSON" "$@" -d "$body"
}

EMAIL_JSON="$(json_string "$EMAIL")"
PASS_JSON="$(json_string "$PASS")"

# --- 1. registro (nace con el rol base "Cliente") --------------------------
STATUS=$(request POST "$BASE/auth/register" \
  "{\"email\":\"$EMAIL_JSON\",\"password\":\"$PASS_JSON\",\"name\":\"QA\",\"lastName\":\"Demo\"}")
RESP=$(cat "$BODY_FILE")
if [ "$STATUS" != "201" ]; then
  echo "El registro fallo (HTTP $STATUS): $RESP" >&2
  echo "Si el correo ya existe, crea el usuario por otro medio y llama al PUT de roles." >&2
  exit 1
fi

USER_ID=$(printf '%s' "$RESP" | sed -n 's/.*"userId":\([0-9]*\).*/\1/p')
if [ -z "$USER_ID" ]; then
  echo "El registro respondio 201 pero sin userId: $RESP" >&2
  exit 1
fi
echo "== 1. registrado: $EMAIL (userId=$USER_ID)"

# --- 2. asignacion de roles (reemplaza el conjunto completo) ---------------
ROLES_BODY='{"roles":['
for i in "${!ROLES[@]}"; do
  if [ "$i" -gt 0 ]; then
    ROLES_BODY="$ROLES_BODY,"
  fi
  ROLES_BODY="$ROLES_BODY\"$(json_string "${ROLES[$i]}")\""
done
ROLES_BODY="$ROLES_BODY]}"

STATUS=$(request PUT "$BASE/api/internal/users/$USER_ID/roles" "$ROLES_BODY" \
  -H "X-Internal-Api-Key: $INTERNAL_API_KEY")
RESP=$(cat "$BODY_FILE")
if [ "$STATUS" != "200" ]; then
  echo "La asignacion de roles fallo (HTTP $STATUS): $RESP" >&2
  exit 1
fi
echo "== 2. roles asignados: $RESP"

# --- 3. login (el token ya viaja con los roles) ----------------------------
STATUS=$(request POST "$BASE/auth/login" \
  "{\"login\":\"$EMAIL_JSON\",\"password\":\"$PASS_JSON\"}")
RESP=$(cat "$BODY_FILE")
if [ "$STATUS" != "200" ]; then
  echo "El login fallo (HTTP $STATUS): $RESP" >&2
  exit 1
fi

ACCESS=$(printf '%s' "$RESP" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
REFRESH=$(printf '%s' "$RESP" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')
if [ -z "$ACCESS" ] || [ -z "$REFRESH" ]; then
  echo "El login no devolvio tokens: $RESP" >&2
  exit 1
fi

# Los roles viajan en el claim 'roles' del JWT. Se decodifica el payload para
# comprobar que el token trae lo que se pidio: si no, el error aparece ahora y
# no tres requests mas adelante.
PAYLOAD=$(printf '%s' "$ACCESS" | cut -d. -f2)
PAD=$(( 4 - ${#PAYLOAD} % 4 ))
if [ "$PAD" -ne 4 ]; then
  PAYLOAD="$PAYLOAD$(printf '=%.0s' $(seq 1 "$PAD"))"
fi
CLAIMS=$(printf '%s' "$PAYLOAD" | tr '_-' '/+' | base64 -d 2>/dev/null)

if [ -z "$CLAIMS" ]; then
  # Sin payload legible no se puede verificar nada: el paso de verificacion
  # no se saltea en silencio.
  echo "No se pudo decodificar el token para verificar los roles." >&2
  exit 1
fi

# La comparacion no distingue mayusculas: el servicio resuelve "repartidor" al
# nombre canonico "Repartidor" y devuelve ese en el claim. Comparar en crudo
# daria un fallo falso cuando QA pide el rol en minuscula.
CLAIMS_LOWER=$(printf '%s' "$CLAIMS" | tr '[:upper:]' '[:lower:]')
for rol in "${ROLES[@]}"; do
  ROL_LOWER=$(printf '%s' "$rol" | tr '[:upper:]' '[:lower:]')
  case "$CLAIMS_LOWER" in
    *"\"$ROL_LOWER\""*) ;;
    *)
      echo "El token NO trae el rol $rol. Claims: $CLAIMS" >&2
      exit 1
      ;;
  esac
done
echo "== 3. claims del token: $CLAIMS"

echo "== access token =="
echo "$ACCESS"
echo
echo "== refresh token =="
echo "$REFRESH"

#!/usr/bin/env bash
# Arranca auth-service contra su base PostgreSQL.
#   bash services/auth-service/run-auth.sh
set -e

# Rutas relativas al script, no al directorio desde donde se invoca.
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # services/auth-service
RAIZ_GRADLE="$(dirname "$AQUI")"                        # services

# Algunos equipos del team tienen el JAVA_HOME del sistema mal escrito.
# Solo se corrige si el que hay no apunta a un JDK valido.
if [ ! -x "${JAVA_HOME:-/no-existe}/bin/java" ] && [ -d "/c/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot" ]; then
  export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
fi

# Variables desde services/auth-service/.env si existe.
if [ -f "$AQUI/.env" ]; then
  set -a; . "$AQUI/.env"; set +a
fi

# Para la primera prueba, clave RSA efimera si no se definieron las claves.
: "${JWT_ALLOW_EPHEMERAL:=true}"
export JWT_ALLOW_EPHEMERAL

# Conexion a la base propia en Floci RDS, mas la clave compartida entre
# servicios. Sin alguna de las cuatro el servicio no deberia arrancar.
for var in DB_URL DB_USERNAME DB_PASSWORD INTERNAL_API_KEY; do
  if [ -z "$(eval echo "\${$var:-}")" ]; then
    echo "ERROR: falta $var. Definila en services/auth-service/.env (ver .env.example)."
    exit 1
  fi
done

echo "Arrancando auth-service..."
cd "$RAIZ_GRADLE"
./gradlew :auth-service:bootRun

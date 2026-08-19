#!/usr/bin/env bash
# Arranca auth-service contra Supabase.
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

export SPRING_PROFILES_ACTIVE=supabase

# Variables desde services/auth-service/.env si existe.
if [ -f "$AQUI/.env" ]; then
  set -a; . "$AQUI/.env"; set +a
fi

# Para la primera prueba, clave RSA efimera si no se definieron las claves.
: "${JWT_ALLOW_EPHEMERAL:=true}"
export JWT_ALLOW_EPHEMERAL

# La persistencia es PostgREST, no JDBC: lo que hace falta es la URL y la
# service_role key del proyecto Supabase propio de auth (MIGRATION_PLAN 4.1),
# mas la clave compartida entre servicios.
for var in SUPABASE_URL SUPABASE_SERVICE_ROLE_KEY INTERNAL_API_KEY; do
  if [ -z "$(eval echo "\${$var:-}")" ]; then
    echo "ERROR: falta $var. Definila en services/auth-service/.env (ver .env.example)."
    exit 1
  fi
done

echo "Arrancando auth-service (perfil supabase)..."
cd "$RAIZ_GRADLE"
./gradlew :auth-service:bootRun

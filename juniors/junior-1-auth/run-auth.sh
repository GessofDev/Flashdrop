#!/usr/bin/env bash
# Arranca auth-service contra Supabase. Uso: bash run-auth.sh
set -e

# JDK 21 (corrige el JAVA_HOME del sistema si está mal)
export JAVA_HOME="C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.8.9-hotspot"
export SPRING_PROFILES_ACTIVE=supabase

# Carga las variables desde auth-service/.env si existe
if [ -f auth-service/.env ]; then
  set -a; . auth-service/.env; set +a
fi

# Para la primera prueba usa clave RSA efímera si no definiste las claves
: "${JWT_ALLOW_EPHEMERAL:=true}"
export JWT_ALLOW_EPHEMERAL

if [ -z "${DB_PASSWORD:-}" ]; then
  echo "ERROR: falta DB_PASSWORD. Créalo en auth-service/.env (ver .env.example)."
  exit 1
fi

echo "Arrancando auth-service (perfil supabase)..."
./gradlew :auth-service:bootRun

# FlashDrop Microservices Monorepo

Arquitectura de microservicios basada en **Spring Boot 3**, **Java 21** y **Gradle Multi-proyecto**.

## 🚀 Cómo Empezar (Guía para Desarrolladores)

### 1. Prerrequisitos
- **Java 21** instalado (`$env:JAVA_HOME` configurado correctamente).
- **Docker Desktop** o similar corriendo.

### 2. Variables de Entorno
Copia el archivo de ejemplo y configura tus valores locales:
```powershell
cp .env.example .env
```
*(Nota: El archivo `.env` está en el `.gitignore` para no subir secretos).*

### 3. Levantar la Base de Datos
El proyecto requiere PostgreSQL. Levántalo usando Docker Compose:
```powershell
docker compose up -d
```
Esto levantará la BD expuesta en el puerto `5432` con las credenciales de tu `.env`.

### 4. Ejecutar los Microservicios
Levanta los servicios que necesites. En consolas separadas:

**Auth Service (Migraciones, Login, Registro):**
```powershell
.\gradlew.bat :auth-service:bootRun
```
*Importante:* El `auth-service` es el **único** responsable de ejecutar Flyway (creación de tablas). Levántalo primero.

**Catalog Service (Productos, Categorías, Restaurantes):**
```powershell
.\gradlew.bat :catalog-service:bootRun
```
*Nota:* Por defecto el catalog arranca con el perfil `local` (datos en memoria). Para apuntarlo a la BD, levántalo así:
```powershell
.\gradlew.bat :catalog-service:bootRun --args="--spring.profiles.active=default"
```

## 🏗️ Arquitectura y Reglas

- **IDs:** Todos los microservicios usan `UUID` (no autoincrementales numéricos).
- **Seguridad:** Autenticación vía JWT (RS256). Solo `auth-service` emite tokens. El resto de los servicios solo los **validan** usando la Clave Pública.
- **Base de Datos Compartida:** Durante el desarrollo temprano, todos los servicios apuntan a `flashdrop_auth` pero a nivel de esquema actúan como si fueran separados. Las migraciones SQL se agregan en `auth-service/src/main/resources/db/migration/`.

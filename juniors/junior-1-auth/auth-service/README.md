# Auth Service

Servicio de autenticación para FlashDrop. 
Se encarga del registro de usuarios, login, refresco de tokens y manejo de sesión.

## Dependencias
- Spring Boot 3.x
- Supabase (PostgREST API) para base de datos

## Endpoints Principales
- `POST /auth/register`: Registrar un nuevo usuario
- `POST /auth/login`: Autenticarse y obtener tokens
- `POST /auth/refresh`: Refrescar el token de acceso
- `POST /auth/logout`: Cerrar sesión

## Levantamiento
Para levantar localmente:
```bash
./gradlew :auth-service:bootRun -Dspring.profiles.active=supabase
```

## Variables de Entorno Requeridas
(Revisar el archivo `.env.example`)
- `SPRING_PROFILES_ACTIVE=supabase`
- `SUPABASE_URL`: URL del API Gateway Kong (PostgREST)
- `SUPABASE_SERVICE_ROLE_KEY`: Service role key para acceso por API
- `JWT_ALLOW_EPHEMERAL=true` o las respectivas claves JWT.

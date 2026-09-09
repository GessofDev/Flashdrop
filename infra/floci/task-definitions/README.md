# Task definitions

Un archivo por servicio. Se registran contra Floci con:

```bash
aws --endpoint-url http://127.0.0.1:4566 ecs register-task-definition \
  --cli-input-json file://infra/floci/task-definitions/auth-service.json
```

El mismo archivo sirve contra AWS real quitando `--endpoint-url`, que es el
objetivo de paridad que plantea `../INFRASTRUCTURE.md`.

## Antes de registrar: crear los secretos

Los `valueFrom` apuntan a secretos que tienen que existir. Se crean una vez:

```bash
aws --endpoint-url http://127.0.0.1:4566 secretsmanager create-secret \
  --name auth/db-password --secret-string 'LA-CLAVE'
```

El valor **nunca** va en este JSON ni en ningún archivo del repositorio: este
repositorio es público. La clave de RDS tampoco se puede recuperar desde la UI
de Floci, así que además tiene que estar guardada en el gestor de secretos del
equipo.

Secretos que espera auth-service:

| Secreto | Contenido |
| --- | --- |
| `auth/db-password` | clave del usuario `auth_app` en `flashdrop_auth` |
| `flashdrop/internal-api-key` | la clave compartida entre los 5 servicios (`openssl rand -hex 32`) |
| `auth/jwt-private-key` | clave privada RSA en PEM |
| `auth/jwt-public-key` | clave pública RSA en PEM |

## Notas sobre auth-service.json

**Las claves JWT son obligatorias.** `jwt.allow-ephemeral-key` es `false` por
defecto y así debe quedar. Ponerlo en `true` para que la tarea levante parece
un atajo, pero con más de una réplica cada tarea firmaría con una clave
distinta y el JWKS de una no validaría los tokens de la otra: los logins
fallarían de forma intermitente según a qué tarea caiga cada request.

**`FLYWAY_LOCATIONS` incluye el seed porque esto es desarrollo.** El valor por
defecto de la aplicación es sólo `classpath:db/migration` — el esquema, sin
datos. En un entorno productivo hay que **quitar esta variable**: si el seed se
aplica ahí, quedan usuarios demo con credenciales conocidas y Flyway lo
registra como aplicado, así que no vuelve a intentarlo ni deja rastro del
error. La base de desarrollo actual ya tiene la `V2` aplicada, y por eso
necesita la variable: sin ella Flyway falla al no encontrar localmente una
migración que la base dice tener.

**`DB_URL` apunta a `172.16.1.2`**, que es el contenedor de Floci haciendo de
proxy hacia el Postgres de `flashdrop-auth-postgres`. El puerto lo asigna Floci
de forma dinámica dentro del rango 7001-7010: si alguien recrea la instancia,
hay que confirmarlo con

```bash
aws --endpoint-url http://127.0.0.1:4566 rds describe-db-instances \
  --query 'DBInstances[].{id:DBInstanceIdentifier,port:Endpoint.Port}' --output table
```

**Logs.** No se declara `logConfiguration` porque está sin verificar si Floci
emula CloudWatch Logs; `INFRASTRUCTURE.md` lo marca como pendiente de
comprobar. Mientras tanto, `docker logs`.

## Estado

| Servicio | Task definition |
| --- | --- |
| auth-service | ✅ este archivo |
| catalog-service | pendiente |
| orders-service | pendiente |
| delivery-service | pendiente |
| gateway | pendiente |

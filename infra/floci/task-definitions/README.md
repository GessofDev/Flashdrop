# Task definitions

Un archivo por servicio y por entorno: `<servicio>.<entorno>.json`. Hoy solo
existe `dev`, que es Floci. El equivalente productivo del mismo servicio se
diferencia sobre todo en dos cosas — no lleva `FLYWAY_LOCATIONS` y apunta a
otra base — así que conviene que el entorno se lea en el nombre del archivo y
no haya que abrir el JSON para saber a qué apunta.

Se registran contra Floci con:

```bash
aws --endpoint-url http://127.0.0.1:4566 ecs register-task-definition \
  --cli-input-json file://infra/floci/task-definitions/auth-service.dev.json
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

## Notas sobre auth-service.dev.json

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

| Servicio | Task definition | Listo para registrar |
| --- | --- | --- |
| auth-service | `auth-service.dev.json` | sí |
| catalog-service | `catalog-service.dev.json` | sí |
| orders-service | `orders-service.dev.json` | falta confirmar `FLYWAY_ENABLED` |
| delivery-service | `delivery-service.dev.json` | sí |
| gateway | `gateway.dev.json` | falta hornear el YAML en la imagen |

## Lo que falta antes de registrarlas

**`FLYWAY_ENABLED` de orders está en `false`, y no es arbitrario.** Se consultó
la base el 2026-09-10 y devolvió tres tablas —`client`, `order_items`,
`orders`— **sin `flyway_schema_history`**. O sea que alguien creó ese esquema
fuera de Flyway.

Con `true`, orders no arranca: corta con *Found non-empty schema without schema
history table*, porque no define `baseline-on-migrate`. Es el caso que el propio
comentario GAP-06 de su `application.properties` anticipaba.

`false` desbloquea el despliegue sin tocar código: las tablas ya están y
`ddl-auto` es `none`, así que el servicio trabaja contra lo que hay. **Pero es
un parche**: mientras siga así, ninguna migración futura de orders se va a
aplicar, y nadie va a enterarse hasta que falte una columna.

La solución de fondo es agregar `spring.flyway.baseline-on-migrate=true` en
orders. Flyway crea entonces la tabla de historial, marca la `V1` como aplicada
—que es lo correcto, porque las tablas existen— y de ahí en adelante las
migraciones nuevas corren normalmente. Es una línea, y es de orders. Cuando esté,
esta variable vuelve a `true`.

Para volver a consultar el estado de la base:

```sql
select table_name from information_schema.tables where table_schema = 'public';
```

**El gateway no tiene su configuración dentro de la imagen.** Su Dockerfile no
copia ningún YAML, aunque declara `CONFIG_PATH=/app/config/gateway.yaml`: esa
ruta existe solo por el volumen que monta el compose, y en Fargate no hay
volúmenes del host. Hasta que se agregue el `COPY`, esta task definition
registra pero el contenedor no encuentra su configuración.

Las URLs y el JWKS ya van como variables de entorno acá, que es la forma
acordada: el loader del gateway interpola `${VAR}` en el YAML y aborta con
`MissingEnvVarError` si falta alguna.

**Redis no tiene task definition.** El gateway lo necesita para el rate
limiting. Hay que decidir si va como una tarea más o como un contenedor aparte.

**Los nombres DNS entre servicios dependen de service discovery.** Las URLs
`http://auth-service:8081` funcionan en Docker Compose por el DNS de la red;
en ECS hace falta registrar el service discovery, que todavía no está hecho.

**Las imágenes apuntan a etiquetas locales.** Cuando se empujen a ECR hay que
cambiarlas por
`000000000000.dkr.ecr.us-east-1.localhost:5100/flashdrop/<servicio>`, y antes
comprobar que ese host resuelva desde dentro de una tarea.

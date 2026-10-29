# auth-service

Emisor de identidad de FlashDrop: registro, login, refresco de tokens y JWT
RS256. Spring Boot 3.3.5, Java 21, arquitectura hexagonal. Puerto `8081`.

Es dueño exclusivo de cinco tablas —`users`, `login`, `roles`,
`user_has_roles`, `refresh_tokens`— en su propia base. Ninguna otra tabla del
sistema le pertenece, y ningún otro servicio lee las suyas: Orders, Catalog y
Delivery obtienen los datos de usuario por `GET /api/internal/users/{id}`.

## Levantarlo

```bash
cp .env.example .env     # y completar DB_URL, DB_USERNAME, DB_PASSWORD, INTERNAL_API_KEY
bash run-auth.sh
```

El script valida que estén las cuatro variables obligatorias y falla con un
mensaje claro si falta alguna. No hay perfil que activar.

La base no se levanta localmente: vive en Floci RDS
(`flashdrop-auth-postgres`). Flyway crea el esquema al arrancar; ver
[`db/README.md`](db/README.md) para el detalle de las migraciones y del seed.

Para construir la imagen, el contexto es `services/`, no esta carpeta —
auth-service depende del módulo hermano `shared-observability`:

```bash
docker build -t auth-service:latest -f auth-service/Dockerfile .
```

## Endpoints

| Método | Ruta | Para qué |
|---|---|---|
| POST | `/auth/register` | registrar un usuario |
| POST | `/auth/login` | autenticarse y obtener tokens |
| POST | `/auth/refresh` | refrescar el access token |
| POST | `/auth/logout` | cerrar sesión |
| GET | `/auth/profile` | datos del usuario del token |
| GET | `/auth/validate` | validar un token |
| GET | `/auth/.well-known/jwks.json` | clave pública, para que el gateway y delivery validen los JWT |
| GET | `/health` · `/actuator/health` | estado del servicio |

Internos, consumidos por Orders y Delivery (MIGRATION_PLAN sección 3.1):

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/api/internal/users/{userId}` | datos de un usuario |
| GET | `/api/internal/users/by-email?email=...` | resolver un correo a su usuario e ID |
| GET | `/api/internal/users?ids=1,2,3` | variante batch, para no hacer una llamada por registro al listar |
| GET | `/api/internal/users/{userId}/roles` | roles asignados |
| PUT | `/api/internal/users/{userId}/roles` | reemplazar el conjunto de roles de un usuario |

Todos los `/api/internal/**` exigen la cabecera `X-Internal-Api-Key`. Si
`INTERNAL_API_KEY` no está definida, el filtro responde 403 a todo: falla
cerrado a propósito. El contrato completo está en
[`openapi.yaml`](openapi.yaml).

### Asignar roles a un usuario

El registro siempre nace con el rol base `Cliente`. Para darle otros roles
(repartidor, restaurante) está el `PUT /api/internal/users/{userId}/roles`, que
**reemplaza** el conjunto completo: lo que no va en el cuerpo deja de estar
asignado, y una lista vacía deja al usuario sin roles.

```bash
{"roles": ["Repartidor"]}
```

Los nombres se resuelven contra la tabla `roles` y no distinguen mayúsculas;
la respuesta devuelve siempre el nombre canónico. Un rol que no existe se
rechaza con 400 y no toca lo que ya estaba asignado.

Los roles viajan en el claim `roles` del JWT, que se emite al hacer login:
después de asignar roles hay que **volver a autenticarse** para que el token
los traiga.

**Sobre quién puede llamar**: hoy la única puerta es la clave interna que
comparten los cinco despliegues, así que cualquier servicio que la tenga
puede asignar roles a cualquier usuario. La auditoría registra el principal
(`internal-api`), el origen de la petición y el usuario afectado, pero no hay
identidad de llamador. Cuando exista el panel de administración hay que sumar
una identidad real —un rol `Admin` o una credencial por servicio— antes de
exponer esto más allá de QA.

Es la misma operación que consumirá el panel de administración cuando exista.
Mientras tanto, el script de QA hace el flujo completo —alta, roles y login—
en un comando:

```bash
BASE=http://localhost:8081 INTERNAL_API_KEY=<clave-del-entorno> \
  ./scripts/qa-create-user.sh qa.repartidor@flashdrop.cl Segura1234 Repartidor
```

Para crear una cuenta de comercio y vincularla al restaurante ID 2 de FloCI
(`Flash Restaurant Demo` en su seed), indica `--restaurant-id` y confirma el
nombre esperado. El script verifica el nombre antes de crear la cuenta, ya que
los IDs de la instancia local en memoria no coinciden con los de FloCI:

```bash
BASE=http://localhost:8081 CATALOG_BASE=http://localhost:8082 \
  INTERNAL_API_KEY=<clave-del-entorno> \
  ./scripts/qa-create-user.sh comercio@ejemplo.cl '<clave-segura>' Restaurante \
    --restaurant-id 2 --restaurant-name 'Flash Restaurant Demo'
```

El script crea la cuenta, reemplaza sus roles por los indicados y, si se
solicita un comercio, asigna el `userId` devuelto por Auth al restaurante en
Catalog. El endpoint de asignación reemplaza al dueño actual; úsalo solo tras
confirmar el ID y el comercio destino. Si el correo ya existe, el registro
falla intencionalmente: resuelve su ID con `GET /api/internal/users/by-email`
y ejecuta el `PUT /api/internal/restaurants/{restaurantId}/owner` manualmente.

## Decisiones que conviene no deshacer sin leer

- **IDs numéricos**, `bigint generated by default as identity`, con los del
  seed fijos y coordinados con los otros tres servicios. Están explicados en
  [`db/README.md`](db/README.md).
- **Hibernate solo valida el esquema** (`ddl-auto: validate`). Quien crea y
  altera es Flyway, y nadie más.
- **Sin valores por defecto para la conexión ni para la clave interna.** Si
  falta una, el servicio no arranca o no atiende, en vez de quedar sirviendo
  contra algo equivocado sin que nadie se entere.
- **Las claves JWT son obligatorias fuera de desarrollo local.** Con más de una
  réplica y clave efímera, cada instancia firma con una clave distinta y los
  logins fallan de forma intermitente.

## Tests

```bash
cd .. && ./gradlew :auth-service:test --configure-on-demand
```

142 tests. Los 19 de integración (12 de `AuthPostgresIT` y 7 de
`AuthRoleAssignmentIT`) levantan un PostgreSQL real con Testcontainers y se
omiten solos si no hay Docker, así que no rompen el build en equipos que no
pueden levantar contenedores. Sí corren en CI:
`.github/workflows/auth-service-ci.yml` ejecuta `./gradlew :auth-service:build`
sobre `ubuntu-latest`, que tiene Docker.

Cubren los mínimos de MIGRATION_PLAN sección 12.1 —unitarios de registro,
autenticación y refresco; integración de los endpoints internos; el test de
contrato que fija los campos exactos de `/api/internal/users/{id}`— más los
hallazgos de la auditoría QA: canal lateral por tiempo en el login, cobertura
de JWT, formato de error por superficie y CORS.

## Despliegue

La task definition para ECS sobre Floci está en
[`infra/floci/task-definitions/`](../../infra/floci/task-definitions/), con los
secretos por referencia a Secrets Manager. Nunca literales: este repositorio es
público.

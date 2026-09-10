# FloCI Infrastructure — FlashDrop Dev Environment

> **Status**: Living document. Updated as the FloCI deployment evolves toward production parity.
> **Last update**: 2026-09-10 — se corrigieron la tabla de bases, la cobertura
> de servicios y los ejemplos de secretos, que estaban desactualizados.

## 1. Purpose

FloCI is a local AWS emulator that runs on the dev VPS (`76.13.168.23`). We use it to
validate infrastructure, networking, secrets, and deployments in an environment that
mirrors production AWS, so the day we switch from `--endpoint-url http://127.0.0.1:4566`
to real AWS the change is plumbing-only.

**Goal**: zero behavioral and operational drift between dev (FloCI) and prod (AWS).
**Today**: we use FloCI for **RDS only**. Containers are started manually via
`docker run` on the VPS host. **Target**: full deploy parity via `aws ecs`,
`aws secretsmanager`, `aws elbv2`, and `aws iam` against FloCI's emulation.

## 2. AWS services we use (and FloCI's coverage)

Verificado contra la instalación el 2026-09-09. La consola web **no muestra ECS
en su menú** y «Compute» resulta ser EC2, así que había motivos para dudar de
que el plan de esta sección fuera ejecutable. La API dice otra cosa, y la API es
la que manda: `ecs list-clusters`, `ecr describe-repositories` y
`elbv2 describe-load-balancers` responden con lista vacía, que es la respuesta
de un servicio emulado sin recursos, no la de uno inexistente.

| AWS service        | FloCI emulation | Used by                              | Status today |
| ------------------ | --------------- | ------------------------------------ | ------------- |
| RDS (Postgres)     | ✅              | All service databases                | **Active** — 4 instancias |
| Secrets Manager    | ✅              | DB passwords, internal API key, JWT  | **Active** — 5 secretos |
| ECS                | ✅ verificado   | All microservice runtimes            | Cluster `flashdrop-dev` creado |
| ECR                | ✅ verificado   | Imágenes de los 5 servicios          | 5 repositorios creados |
| ELBv2 (ALB / NLB)  | ✅ verificado   | Public-facing load balancing         | Sin recursos todavía |
| IAM / STS          | ✅              | Task roles, cross-service auth       | Planned       |
| CloudWatch Logs    | ✅ verificado   | Logs de las tareas ECS              | **Active** — Floci ya crea grupos por instancia RDS |
| S3, SQS, SNS       | ✅ (not used)   | Reserved for future needs            | n/a           |
| Lambda             | ✅ (not used)   | Reserved for future needs            | n/a           |

**CloudWatch Logs está emulado.** Verificado el 2026-09-10: `describe-log-groups`
responde, y Floci crea grupos por su cuenta — uno por instancia RDS
(`/aws/rds/instance/<nombre>/error`) y otro para el registro de imágenes. Las
task definitions declaran sus logs en `/ecs/<servicio>` con
`awslogs-create-group`, así que no hay que crearlos a mano:

```bash
aws --endpoint-url http://127.0.0.1:4566 logs tail /ecs/auth-service --follow
```

El registro de imágenes responde en
`000000000000.dkr.ecr.us-east-1.localhost:5100`. Falta comprobar que ese host
resuelva desde dentro de una tarea antes de apuntar ahí las task definitions.

Sources: FloCI project documentation (https://github.com/floci/floci).

## 3. Network layout

All containers live on a single Docker bridge network `floci_default`
(subnet `172.16.1.0/24`). This is intentional — FloCI is a single-host emulator, not a
multi-AZ simulator. VPC/subnet isolation patterns from real AWS are not modeled here.

```
floci_default (172.16.1.0/24)
├── floci                       172.16.1.2   — FloCI API (port 4566 on host)
├── floci-ui                    172.16.1.3   — FloCI UI dashboard (port 4566 on host)
├── floci-rds-db-...-<hash>     .4, .5, ...  — one RDS instance per service DB
│   ├── 1682BEA7BEF543F588620180-9be317     auth_db
│   ├── E9A772CBA2C746069F1EB850-9ee6e5      catalog_db
│   ├── 2F45111D873D47A1ACD3248A-d98b83      orders_db
│   ├── 78046697790640FAB95C2DF6-42b0ca      (reserved)
│   └── FF3A6090C70640E4A658B6A7-4c8f94      delivery_db  (port 7005 on host)
└── (ECS service containers, planned)
    ├── delivery-service         172.16.1.9  (currently via docker run, target via ECS)
    ├── orders-service          TBD
    ├── catalog-service         TBD
    └── auth-service            TBD
```

External access:
- `localhost:4566`  → FloCI API (for `aws --endpoint-url`)
- `localhost:4566/ui` → FloCI UI
- `localhost:7005`  → delivery_db Postgres (FloCI's port mapping for the delivery RDS)
- `localhost:8081/8082/8083/8084` → service HTTP (planned via ALB, today via raw port mapping)

## 4. Current state — what's actually running

### Databases (created via AWS CLI against FloCI)

**Las cuatro existen.** Ninguna está pendiente de crear. Datos verificados con
`rds describe-db-instances` el 2026-09-09:

| Servicio | Instancia                    | Endpoint          | Base                | Usuario        |
| -------- | ---------------------------- | ----------------- | ------------------- | -------------- |
| auth     | `flashdrop-auth-postgres`    | `172.16.1.2:7004` | `flashdrop_auth`    | `auth_app`     |
| catalog  | `flashdrop-catalog-postgres` | `172.16.1.2:7001` | `flashdrop_catalog` | `catalog_app`  |
| orders   | `flashdrop-orders-postgres`  | `172.16.1.2:7002` | `flashdrop_orders`  | `orders_app`   |
| delivery | `delivery-rds`               | `172.16.1.2:7005` | `delivery_db`       | `delivery_svc` |

Las cuatro son PostgreSQL 16.3 y salen por `172.16.1.2`, que es el contenedor de
FloCI haciendo de proxy.

**Ojo con los nombres de usuario.** Son `<servicio>_app`, no `<servicio>_svc`
como dice el script de bootstrap — salvo delivery, que sí quedó como
`delivery_svc`. Las contraseñas no se listan acá: viven en Secrets Manager.

**Los puertos son dinámicos.** FloCI los asigna dentro del rango 7001-7010 al
crear cada instancia, y se reordenan si alguien recrea una. En agosto el 7002
era una instancia de prueba. Ningún puerto debe quedar escrito como valor por
defecto en el código; para confirmarlos:

```bash
aws --endpoint-url http://127.0.0.1:4566 rds describe-db-instances   --query 'DBInstances[].{id:DBInstanceIdentifier,port:Endpoint.Port,db:DBName,user:MasterUsername}'   --output table
```

**`infra/coolify/01-postgres-init.sql` NUNCA se corrió contra FloCI.** Se
escribió para Coolify y crea usuarios `<servicio>_svc`; las instancias de FloCI
se crearon aparte, con los usuarios de la tabla de arriba. Esta confusión ya
hizo perder tiempo dos veces —una creyendo que `delivery_svc` no existía, otra
creyendo que había que provisionar `orders_db`—, así que: **no hace falta correr
ese script; las bases y sus usuarios ya están.**

**Ninguna contraseña va en este archivo.** Este repositorio es público. Antes
había una en texto plano acá y se eliminó; esa clave debe considerarse
comprometida. Todas viven en Secrets Manager.

### Secrets

Convención: `<servicio>/db-password`, uno por servicio y creado por su dueño.

| Secreto                      | Estado  |
| ---------------------------- | ------- |
| `flashdrop/internal-api-key` | creado  |
| `auth/db-password`           | creado  |
| `auth/jwt-private-key`       | creado  |
| `auth/jwt-public-key`        | creado  |
| `delivery/db-password`       | creado  |
| `catalog/db-password`        | pendiente |
| `orders/db-password`         | pendiente |

**`flashdrop/internal-api-key` es UNA SOLA para los cinco servicios.** No crear
una por servicio: en cuanto alguien rote una y no las otras, todas las llamadas
a `/api/internal/**` empiezan a devolver 403 y el síntoma no apunta a la causa.
Referenciarla por su ARN:

```
arn:aws:secretsmanager:us-east-1:000000000000:secret:flashdrop/internal-api-key-REB7PU
```

**El par RSA de los JWT es nuevo.** El que estaba versionado en
`gateway/docker/secrets/jwt-private.pem` quedó publicado y hay que darlo por
comprometido; el reemplazo es el de la tabla. Crear un secreto sin que la clave
aparezca en pantalla ni en el historial del shell:

```bash
read -s -p "Clave: " CLAVE; echo
aws --endpoint-url http://127.0.0.1:4566 secretsmanager create-secret   --name <servicio>/db-password --secret-string "$CLAVE"
unset CLAVE
```

Antes de cualquier comando `aws`, en cada sesión nueva:

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
```

FloCI no valida credenciales — su propia consola muestra la cuenta como
`0000-0000-0000`. Sin ese `export`, los comandos fallan con `NoRegion`.

### Service containers (today: manual `docker run`)

- **delivery-service** sigue siendo el único servicio corriendo, levantado a
  mano, y ocupa el puerto **8084**. Hay que apagarlo antes de levantar el stack
  completo: si no, el contenedor nuevo no puede bindear ese puerto.
  - Perfil: `delivery,mock-orders` — está hablando con un **mock** de orders, no
    con el servicio real. Se levantó cuando orders-service no existía, así que
    cualquier prueba hecha contra ese puerto pasó por un simulador.
  - Su `DELIVERY_DB_PASSWORD` ya quedó guardada en Secrets Manager como
    `delivery/db-password`.

El resto del stack se levanta con
`gateway/docker/docker-compose.stack.yml`, que apunta a las bases de la tabla de
arriba. Cada servicio va en **dos redes**: la del stack, que da el DNS interno,
y `floci_default`, porque `172.16.1.2` no se alcanza desde otro bridge.

### What this looks like at the moment

```
[ Mobile / Postman ]
        │
        │ HTTP (via VPS tunnel)
        ▼
[ delivery-service :8084 ]   ◄──── docker run --network floci_default
        │
        │ JDBC
        ▼
[ delivery_db (172.16.1.8:7005) ]   ◄──── FloCI RDS
```

There is no gateway, no ALB, no public-facing load balancing yet. Everything
talks to `delivery-service` directly on port 8084 via the VPS tunnel.

## 5. Target state — production-parity deploy

The end state is "deploy via `aws` CLI the same way against FloCI and real AWS":

```
[ Mobile / Postman ]
        │
        │ HTTPS
        ▼
[ ALB (FloCI ELBv2) ]
        │
        │ HTTP (internal)
        ├──► [ gateway-service ]   (planned)
        │            │
        │            ├──► auth-service        ─► auth_db      (RDS via Secrets Mgr)
        │            ├──► catalog-service     ─► catalog_db
        │            ├──► orders-service      ─► orders_db
        │            └──► delivery-service    ─► delivery_db  (mock-orders profile off)
        │
        ▼
[ Secrets Manager ]   DB passwords, internal.api.key, JWT signing key
[ IAM / STS ]         Task roles per service
```

Every box labelled `service` is an ECS task definition registered against FloCI's
ECS emulation, and the same task definition will run unmodified against real AWS
ECS — only the `--endpoint-url` flag in the `aws` CLI changes.

## 6. Migration path from current state to target

Step-by-step, in order. Each step is independently testable; do not skip.

### Step 1 — Register task definition

Today the service is started via raw `docker run`. Replace with:

```bash
aws --endpoint-url http://127.0.0.1:4566 ecs register-task-definition \
  --cli-input-json file://infra/floci/task-definitions/delivery-service.json
```

`delivery-service.json` declares the same image, env vars, and secrets refs that
the current `docker run` uses.

### Step 2 — Migrate secrets to Secrets Manager

**Hecho para 5 de 7** — ver la tabla de la sección 4. Faltan
`catalog/db-password` y `orders/db-password`, que crea cada dueño.

El ejemplo que había acá traía la contraseña literal escrita dentro del comando.
Eso tiene dos problemas: la deja en un archivo versionado de un repositorio
público —esa clave ya se dio por comprometida— y la guarda en el historial del
shell del servidor. La forma correcta está en la sección 4: pedirla con
`read -s`.

**Y no crear una clave interna por servicio.** Es una sola,
`flashdrop/internal-api-key`, compartida por los cinco. Duplicarla es cómo
aparecen los 403 cruzados entre servicios.

Después se referencian con `secrets[].valueFrom` desde la task definition.
**Nunca** el valor literal en el JSON ni en ningún archivo versionado.


### Step 3 — Create cluster + service

```bash
aws --endpoint-url http://127.0.0.1:4566 ecs create-cluster \
  --cluster-name flashdrop-dev
aws --endpoint-url http://127.0.0.1:4566 ecs create-service \
  --cluster flashdrop-dev --service-name delivery-service \
  --task-definition delivery-service:1 --desired-count 1 \
  --launch-type FARGATE --network-configuration "..."
```

### Step 4 — Create target group + ALB

```bash
aws --endpoint-url http://127.0.0.1:4566 elbv2 create-target-group \
  --name delivery-tg --protocol HTTP --port 8084 \
  --health-check-path /actuator/health
aws --endpoint-url http://127.0.0.1:4566 elbv2 create-load-balancer \
  --name delivery-alb --type application ...
aws --endpoint-url http://127.0.0.1:4566 elbv2 create-listener \
  --load-balancer-arn <alb-arn> --protocol HTTP --port 80 \
  --default-actions Type=forward,TargetGroupArn=<tg-arn>
```

### Step 5 — Validate `update-service` reproduces the deploy

After all of the above, a redeploy becomes:

```bash
aws --endpoint-url http://127.0.0.1:4566 ecs update-service \
  --cluster flashdrop-dev --service delivery-service \
  --task-definition delivery-service:2 --force-new-deployment
```

Confirm:
- New task definition pulls and starts
- Health check on the ALB target group passes
- Old task drains and stops
- `aws --endpoint-url ... ecs describe-services` shows the new deployment succeeded

Once this works in FloCI, the **exact same command** (with the `--endpoint-url`
removed) deploys to real AWS.

## 7. Production parity checklist

When migrating a service from "manual `docker run`" to "ECS via FloCI", the
following must be identical between FloCI and real AWS:

- [ ] Container image (same tag, same SHA)
- [ ] Env vars (names and values, modulo Secrets Manager references)
- [ ] Secrets resolution (Secrets Manager in both, never literals)
- [ ] Health check path and response (`/actuator/health`)
- [ ] Task role IAM permissions (least privilege)
- [ ] Network mode (private subnet for DBs, public for ALB egress)
- [ ] Resource limits (CPU, memory) — FloCI may not enforce but the declaration must match

If a row on this checklist differs between dev and prod, that is a bug to fix
**before** the production deploy, not after.

## 8. Cross-service networking

Today delivery-service can call other services because they all share
`floci_default`. The DNS names `flashdrop-auth:8081`, `flashdrop-orders:8083`,
etc. are **planned** via ECS service discovery / FloCI's internal DNS, not
implemented yet.

Until then: cross-service calls in dev are by IP (`172.16.1.X:port`). This is
acceptable for dev but **must not** leak into code — every cross-service URL must
come from config / env var, never be hardcoded.

## 9. Operational notes

- **Backups**: FloCI's RDS instances are not backed up. Treat them as
  ephemeral — every DB state we care about lives in Flyway migrations.
- **Container restarts**: FloCI containers restart on demand via the AWS CLI.
  Service containers today restart on VPS reboot because they were started with
  `docker run` (no `--restart=unless-stopped`). Once we move to ECS, restarts
  are managed by FloCI's ECS emulation.
- **Image registry**: today we build images locally on the VPS in
  `/home/dev/dbuild/`. Long-term we want a shared registry (Docker Hub, GHCR,
  or ECR) so the same image runs in CI, dev, and prod.
- **Logs**: `docker logs <container>` only. FloCI does not provide CloudWatch
  Logs emulation yet (verify before assuming).
- **Metrics**: not wired. Add when needed (FloCI likely supports CloudWatch
  metrics — to verify).

## 10. References

- FloCI project: https://github.com/floci/floci
- FloCI docs: see project README for the full list of emulated services
- AWS CLI reference: https://docs.aws.amazon.com/cli/
- `infra/coolify/DEPLOY.md` — Coolify-based deploy (production fallback if
  FloCI proves insufficient)
- `infra/coolify/01-postgres-init.sql` — DB users/grants bootstrap
- `services/delivery-service/CLAUDE.md` — service-level notes (if exists)
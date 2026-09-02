# FloCI Infrastructure — FlashDrop Dev Environment

> **Status**: Living document. Updated as the FloCI deployment evolves toward production parity.
> **Last update**: 2026-08-27.

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

| AWS service        | FloCI emulation | Used by                              | Status today |
| ------------------ | --------------- | ------------------------------------ | ------------- |
| RDS (Postgres)     | ✅              | All service databases                | **Active**    |
| ECS                | ✅              | All microservice runtimes            | Planned       |
| Secrets Manager    | ✅              | DB passwords, internal API key, JWT  | Planned       |
| IAM / STS          | ✅              | Task roles, cross-service auth       | Planned       |
| ELBv2 (ALB / NLB)  | ✅              | Public-facing load balancing         | Planned       |
| S3, SQS, SNS       | ✅ (not used)   | Reserved for future needs            | n/a           |
| Lambda             | ✅ (not used)   | Reserved for future needs            | n/a           |

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

| DB name       | User           | Password (dev)      | Endpoint            | Created  |
| ------------- | -------------- | ------------------- | ------------------- | -------- |
| `delivery_db` | `delivery_svc` | `DevDelivery2026!`  | `172.16.1.8:7005`   | 2026-08  |
| `auth_db`     | `auth_svc`     | _pending_           | _pending_           | —        |
| `catalog_db`  | `catalog_svc`  | _pending_           | _pending_           | —        |
| `orders_db`   | `orders_svc`   | _pending_           | _pending_           | —        |

Bootstrap script: `infra/coolify/01-postgres-init.sql` (used both for Coolify
production and FloCI dev — single source of truth for users/grants per DB).

**Security**: dev passwords only. They will be rotated when the first production
deploy happens. None of these passwords must appear in any committed file other
than this dev-tracked document.

### Service containers (today: manual `docker run`)

- **delivery-service** is currently the only service running.
  - Image: `delivery-service:test` (local Docker build via `dbuild/` standalone context)
  - Profile: `mock-orders` (orders-service and catalog-service don't exist yet)
  - Env: hardcoded `internal.api.key` and `DELIVERY_DB_PASSWORD` — **temporary**,
    will move to Secrets Manager in the migration.

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

Move the hardcoded values into Secrets Manager:

```bash
aws --endpoint-url http://127.0.0.1:4566 secretsmanager create-secret \
  --name delivery/db-password     --secret-string 'DevDelivery2026!'
aws --endpoint-url http://127.0.0.1:4566 secretsmanager create-secret \
  --name delivery/internal-api-key --secret-string 'dev-only-secret-...'
```

Then `secretRef` them from the task definition. **Never** put the literal password
in the task definition JSON or any committed file.

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
## Resumen

Reorganización completa del monorepo para consolidar la arquitectura de microservicios.

### Cambios principales

- **Consolidación de los 4 microservicios en `services/`** (antes vivían en `juniors/<N>-<servicio>/<servicio>-service/`):
  - `services/auth-service/` — Auth con Gradle multi-módulo + `services/shared-observability/`
  - `services/catalog-service/` — Catalog standalone Gradle
  - `services/orders-service/` — Orders standalone Maven
  - `services/delivery-service/` — Delivery standalone Gradle (sin cambios)

- **Archivo del monolito legacy** en `references/monolith/` (Node.js + Express + Vercel + Supabase, lo que está en producción en `flash-drop-delivery.vercel.app`).

- **Documentación de migración** archivada en `references/migration-plan/` (MIGRATION_PLAN.md, scripts de estimación).

- **Cleanup de `juniors/`** (carpeta vacía de archivos tracked, queda solo con build artifacts gitignored locales).

- **Nuevos archivos de deploy** en `infra/coolify/`:
  - `01-postgres-init.sql` — init script: 4 bases + 4 usuarios (least-privilege)
  - `env.shared.template` — variables compartidas entre los 5 deployments
  - `DEPLOY.md` — guía paso a paso para deployar en Coolify

- **README actualizado** en la raíz, reflejando la nueva arquitectura (el viejo README del monolito está preservado en `references/monolith/README.md`).

### Commits incluidos

- `afcdbd2` chore(monorepo): consolidate catalog service from juniors/junior-3-catalog
- `71ddc7f` chore(monorepo): consolidate orders service from juniors/junior-2-orders
- `a76e02e` chore(monorepo): consolidate auth service + shared-observability from juniors
- `66f0ac8` chore(repo): move legacy monolith to references/monolith
- `1c00843` chore(repo): archive remaining juniors/ files and clean up
- `7cc4598` chore(repo): archive migration plan and tools to references/migration-plan

### Cómo verificar

```bash
# Confirmar que la estructura quedó bien
ls services/                    # auth-service, catalog-service, orders-service, delivery-service, shared-observability
ls references/                  # monolith, juniors-history, migration-plan

# Probar que los builds funcionan (después de mergear)
cd services/catalog-service && ./gradlew build -x test   # el más rápido para smoke test
cd services/orders-service && ./mvnw package -DskipTests
cd services/auth-service && ./gradlew build -x test     # requiere gradle-wrapper.jar (verificar)
cd services/delivery-service && ./gradlew bootJar
```

### Pendiente post-merge

1. Configurar **branch protection rules** en `main` (Settings → Branches):
   - Require a pull request before merging
   - Require 1+ approval
   - Require status checks (cuando haya CI por servicio)
   - Include administrators

2. **Limpiar locales** (no commiteados): `juniors/` con build artifacts, y los `.trash-*/` con leftovers de la consolidación. Manual desde PowerShell con `Remove-Item`.

3. **Deployar en Coolify** siguiendo `infra/coolify/DEPLOY.md`.

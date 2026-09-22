# Design: `store-flow`

> Referencia completa: [`docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`](../../../docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md).
> Este archivo es un resumen del design general enfocado a este OpenSpec change.

## Technical Approach

Tres cambios cohesivos:

1. **`orders-service`**: agregar `GET /api/orders/restaurants/{id}/sales-summary` con agregaciones (count, revenue, avg ticket, top products) y validación de ownership.
2. **`catalog-service`**: agregar namespace `/api/catalog/my/products` (CRUD completo con ownership derivada) + `POST /api/catalog/my/products/image` (multipart → S3/MinIO).
3. **`gateway`**: registrar las rutas nuevas.

**Cero migraciones Flyway.** **Cero servicios nuevos.** El upload se monta sobre S3/MinIO (ya disponible en Floci).

## Architecture Overview

```
Flutter (dueño de tienda)
        │
        ▼
   Fastify Gateway (:3000)
        │
   ┌────┼─────────────────┬─────────────────┐
   ▼    ▼                 ▼                 ▼
 auth delivery          orders          catalog
 :8081 :8084            :8083           :8082

 Tocado en este change:
   orders-service: GET /api/orders/restaurants/{id}/sales-summary
   catalog-service: POST /api/catalog/my/products/image
                    POST/GET/PUT/DELETE /api/catalog/my/products
   gateway: 2 rutas nuevas

 Storage nuevo:
   S3/MinIO en Floci (catálogo de imágenes de producto)
```

## Architecture Decisions

### ADR-1 — Métricas en `orders-service`, no en `catalog-service`

**Decisión:** el endpoint vive en `orders-service` y se expone bajo `/api/orders/restaurants/{id}/sales-summary`.
**Alternativas:**
- (A) En `catalog-service`, llamando internamente a `orders-service`. **Rechazado** — suma un hop y un adapter sin beneficio claro.
- (B) En `orders-service`. **Elegido** — el dato es de pedidos, respeta ownership, gateway solo rutea.
**Consecuencias (+):** path semánticamente honesto ("es un agregado sobre pedidos"); una sola fuente de verdad.
**Consecuencias (−):** el cliente Flutter consume un path `/orders/...` para un dato "de mi tienda". Mitigado con naming claro (`sales-summary`).

### ADR-2 — Namespace `/api/catalog/my/*` con ownership derivada del JWT

**Decisión:** nuevo namespace separado. El `restaurantId` nunca viaja en body ni query; se resuelve server-side.
**Alternativas:**
- (A) Mantener `/catalog/products` con authz por rol. **Rechazado** — mezcla lectura pública con escritura autenticada.
- (B) Namespace separado. **Elegido** — refleja el modelo mental del cliente ("estoy en mi panel").
**Consecuencias (+):** separación explícita; nuevo endpoint es imposible que olvide la authz.
**Consecuencias (−):** controller duplicado. Mitigado porque la lógica de dominio (Product) ya está en use cases separados.

### ADR-3 — Upload a S3/MinIO desde el backend

**Decisión:** el cliente Flutter sube a un endpoint del backend (`POST /api/catalog/my/products/image`); el backend sube el binario a S3/MinIO y devuelve la URL.
**Alternativas:**
- (A) Cliente sube directo a S3 con URL pre-firmada. **Rechazado** — requiere generar URLs pre-firmadas por sesión, más complejo para MVP.
- (B) Backend hace proxy de la subida. **Elegido** — centraliza authz y validación (MIME, tamaño).
- (C) Backend almacena en filesystem local. **Rechazado** — problemas con réplicas, backups, CDN.
**Consecuencias (+):** authz y validación en un solo punto; URLs siempre confiables; Floci replica el stack AWS.
**Consecuencias (−):** acopla el backend a S3 (compatible con MinIO, no con GCS o Azure sin abstracción extra). Aceptable para el stack actual.

### ADR-4 — Cache de ownership `userId → restaurantId` con TTL 60s

**Decisión:** cache en memoria (Caffeine) con TTL 60s. Sin invalidación activa.
**Alternativas:**
- (A) Cache distribuido (Redis). **Rechazado** — agrega hop de red y dependencia. Para 60s TTL no vale la pena.
- (B) Cache local con TTL. **Elegido** — simple, suficiente para el caso (un dueño no cambia de restaurante en producción).
**Consecuencias (+):** baja latencia; sin infra adicional.
**Consecuencias (−):** si un admin reasigna el dueño, tarda hasta 60s en propagarse. Mitigable con invalidación activa cuando se implemente el endpoint admin (fuera de scope).

### ADR-5 — Soft delete (no hard delete) en productos

**Decisión:** `DELETE /api/catalog/my/products/{id}` marca `available=false`. No borra la fila.
**Razón:** preservar integridad referencial con `order_items` históricos.
**Consecuencias (+):** sin migraciones; sin orphans; analítica histórica intacta.
**Consecuencias (−):** la tabla crece. Aceptable para MVP.

---

Para más detalle (flujos, errores, riesgos, fuera de alcance) ver el design general en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`.
# Plan de Migración — Orders Service (Felipe)
## Contexto operativo para análisis técnico y desarrollo asistido por IA

> Documento compacto derivado del plan de migración original. Su objetivo es entregar a Antigravity/OpenCode el contexto necesario para analizar y posteriormente desarrollar `Orders Service`, evitando adjuntar el PDF completo.

---

## 1. Objetivo de la migración

FlashDrop Backend fue refactorizado desde un monolito Node.js hacia 4 microservicios Spring Boot con arquitectura hexagonal:

- Auth Service — Nicolás
- Catalog Service — Javier
- Orders Service — Felipe
- Delivery Service — Sebastián

En la situación actual, los 4 servicios comparten una misma base de datos Supabase.

### Objetivo

Cada microservicio debe tener una base de datos independiente, eliminando todo acoplamiento SQL entre servicios.

Reglas:

- Cada tabla pertenece a un único microservicio.
- Ningún servicio puede leer o escribir directamente tablas pertenecientes a otro servicio.
- Las dependencias entre servicios deben resolverse mediante APIs HTTP internas.
- No existen datos en producción; las nuevas BDs pueden crearse desde cero.
- No se requiere migración de datos productivos en vivo.

---

# 2. Ownership de tablas

| Tabla | Servicio dueño | Responsable |
|---|---|---|
| users | Auth | Nicolás |
| login | Auth | Nicolás |
| roles | Auth | Nicolás |
| user_has_roles | Auth | Nicolás |
| refresh_tokens | Auth | Nicolás |
| categories | Catalog | Javier |
| products | Catalog | Javier |
| restaurant | Catalog | Javier |
| orders | Orders | Felipe |
| order_items | Orders | Felipe |
| client | Orders | Felipe |
| delivery | Delivery | Sebastián |
| delivery_routes | Delivery | Sebastián |

### Regla especial: restaurant

`restaurant` pertenece exclusivamente a Catalog.

Aunque Orders y Delivery necesitan información de restaurantes, no pueden consultar directamente la tabla.

Deben consumir Catalog mediante API.

---

# 3. Responsabilidad de Felipe

Felipe es responsable exclusivamente de `Orders Service`.

No debe modificar Auth, Catalog ni Delivery.

Cuando Orders necesite información de otro servicio:

1. identificar la dependencia;
2. solicitar al responsable correspondiente que exponga/complete el endpoint;
3. validar el contrato;
4. consumir la API desde Orders.

### Tablas propias de Orders

- `orders`
- `order_items`
- `client`

---

# 4. Dependencias actuales de Orders

Orders actualmente accede a tablas externas.

Estas dependencias deben reemplazarse por HTTP.

## 4.1 Catalog Service — Javier

### products

Actualmente Orders necesita:

- id
- restaurantId
- name
- description
- price
- image
- isAvailable

Endpoint requerido:

`GET /api/internal/products?ids={id1,id2,...}`

Uso:

- validar productos;
- obtener precio;
- obtener disponibilidad;
- obtener restaurantId.

### restaurant

Actualmente Orders necesita:

`GET /api/internal/restaurants/{restaurantId}`

Respuesta:

- id
- userId
- name
- address

Uso:

- obtener información del restaurante;
- obtener dirección;
- asociar pedido con restaurante.

También existe:

`GET /api/internal/restaurants?userId={userId}`

Retorna el mismo formato y permite obtener el restaurante asociado a un usuario.

---

## 4.2 Auth Service — Nicolás

Orders necesita datos del usuario/cliente.

Endpoint:

`GET /api/internal/users/{userId}`

Respuesta:

```json
{
  "id": 1,
  "name": "Cliente",
  "lastName": "Demo",
  "email": "cliente@demo.cl",
  "phone": "+56911111111"
}
```

Nullabilidad:

- `id`: obligatorio
- `name`: string | null
- `lastName`: string | null
- `email`: string, nunca null
- `phone`: string | null

404:

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "User not found with id: 99"
}
```

---

## 4.3 Delivery Service — Sebastián

### Buscar repartidor

Endpoint:

`GET /api/internal/delivery-persons?userId={userId}`

Respuesta:

```json
{
  "id": 1,
  "userId": 3,
  "vehicle": "Moto"
}
```

404:

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Delivery person not found for userId: 99"
}
```

### Crear ruta

Endpoint:

`POST /api/internal/routes`

Request:

```json
{
  "orderId": 1,
  "pickupAddress": "Av. Providencia 1000",
  "deliveryAddress": "Santa Isabel 060",
  "distanceKm": 2.4,
  "estimatedMinutes": 18,
  "status": "Pendiente"
}
```

Response 201:

```json
{
  "id": 1,
  "orderId": 1,
  "pickupAddress": "Av. Providencia 1000",
  "deliveryAddress": "Santa Isabel 060",
  "distanceKm": 2.4,
  "estimatedMinutes": 18,
  "status": "Pendiente"
}
```

409:

```json
{
  "status": 409,
  "error": "CONFLICT",
  "message": "Route already exists for orderId: 1"
}
```

### Actualizar estado de ruta

Endpoint:

`PATCH /api/internal/routes/{orderId}/status`

Request:

```json
{
  "status": "En camino"
}
```

Response 200:

```json
{
  "id": 1,
  "orderId": 1,
  "status": "En camino"
}
```

---

# 5. API interna que Orders debe exponer

Delivery necesita consultar órdenes.

Orders debe exponer:

`GET /api/internal/orders?ids={id1,id2,...}`

Response 200:

```json
[
  {
    "id": 1,
    "clientId": 1,
    "restaurantId": 1,
    "deliveryId": 1,
    "status": "Preparando",
    "address": "Av. Providencia 1200, Santiago"
  }
]
```

Reglas:

- respuesta siempre es un array;
- IDs inexistentes deben ser ignorados;
- si ningún ID existe, retornar array vacío.

Campos:

- `id`: long, nunca null
- `clientId`: long, nunca null
- `restaurantId`: long, nunca null
- `deliveryId`: long | null
- `status`: string, nunca null
- `address`: string, nunca null

---

# 6. Arquitectura hexagonal

La arquitectura existente debería permitir realizar principalmente cambios en infraestructura/adapters.

Ports relevantes:

- `CatalogPort` en Orders
- `DeliveryPort` en Orders

El plan indica que estos ports ya están definidos.

La lógica de dominio y casos de uso no debería modificarse como parte de la migración, salvo que el análisis del código real demuestre una necesidad concreta.

Objetivo:

SQL/Supabase directo a tablas externas:

`Orders → Supabase → tabla externa`

debe transformarse en:

`Orders → HTTP Client → servicio dueño → BD propia`

---

# 7. Cambios esperados en Orders

## Catalog

Reemplazar el adapter que consulta directamente:

- `products`
- `restaurant`

por un HTTP client/adapter que consuma Catalog.

Nombre propuesto por el plan:

`CatalogHttpClientAdapter`

Debe implementar el port existente correspondiente.

---

## Auth

Reemplazar cualquier acceso directo a `users`.

Orders debe consumir:

`GET /api/internal/users/{id}`

---

## Delivery

Reemplazar cualquier acceso directo a:

- `delivery`

por:

`GET /api/internal/delivery-persons?userId={id}`

Y reemplazar escritura directa sobre:

- `delivery_routes`

por:

`POST /api/internal/routes`

y, si corresponde:

`PATCH /api/internal/routes/{orderId}/status`

---

# 8. Base de datos propia de Orders

Orders debe tener una BD independiente.

Debe contener:

- `orders`
- `order_items`
- `client`

Debe configurarse una conexión Supabase propia.

Variables:

```text
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
```

Debe existir un seed independiente para Orders.

No existe migración de datos productivos porque el plan establece que no hay datos en producción.

---

# 9. Configuración de servicios

Variables estándar:

```text
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY

AUTH_SERVICE_URL
CATALOG_SERVICE_URL
ORDERS_SERVICE_URL
DELIVERY_SERVICE_URL

INTERNAL_API_KEY
```

Para Orders se necesitan:

```text
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
AUTH_SERVICE_URL
CATALOG_SERVICE_URL
DELIVERY_SERVICE_URL
INTERNAL_API_KEY
```

Puertos locales:

| Servicio | Puerto |
|---|---:|
| Auth | 8081 |
| Catalog | 8082 |
| Orders | 8083 |
| Delivery | 8084 |

Configuración esperada conceptualmente:

```yaml
services:
  auth:
    base-url: ${AUTH_SERVICE_URL:http://localhost:8081}
  catalog:
    base-url: ${CATALOG_SERVICE_URL:http://localhost:8082}
  delivery:
    base-url: ${DELIVERY_SERVICE_URL:http://localhost:8084}
  internal-api-key: ${INTERNAL_API_KEY:dev-key}
```

---

# 10. Seguridad interna

Todos los endpoints:

`/api/internal/**`

deben estar protegidos.

El mecanismo definido por el plan es:

Header:

`X-Internal-Api-Key`

Los servicios internos deben validar la clave.

Los endpoints internos:

- no deben estar expuestos al cliente mobile;
- deben rechazar una clave inválida con HTTP 403.

---

# 11. ErrorResponse estándar

Todos los servicios deben utilizar:

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Resource not found with id: 99"
}
```

DTO conceptual:

```java
public record ErrorResponse(
    int status,
    String error,
    String message
) {}
```

Códigos estándar:

| HTTP | error | Uso |
|---:|---|---|
| 400 | BAD_REQUEST | Parámetros/body inválidos |
| 401 | UNAUTHORIZED | Token faltante/inválido |
| 403 | FORBIDDEN | Sin permisos |
| 404 | NOT_FOUND | Recurso inexistente |
| 409 | CONFLICT | Recurso duplicado |
| 422 | VALIDATION_ERROR | Regla de negocio |
| 500 | INTERNAL_ERROR | Error inesperado |
| 503 | SERVICE_UNAVAILABLE | Servicio dependiente caído/timeout |

Orders debe manejar correctamente errores provenientes de Auth, Catalog y Delivery.

---

# 12. Flujo E2E: creación de orden

Flujo esperado:

1. Cliente solicita crear orden.
2. Orders → Catalog:
   `GET /api/internal/products?ids=1,2`
3. Catalog devuelve productos, precios y disponibilidad.
4. Orders → Catalog:
   `GET /api/internal/restaurants/1`
5. Catalog devuelve restaurante y dirección.
6. Orders → Auth:
   `GET /api/internal/users/1`
7. Orders guarda orden + items en su BD.
8. Orders → Delivery:
   `POST /api/internal/routes`
9. Orders responde `201 Created`.

---

# 13. Flujo E2E: repartidor reclama órdenes

Aunque Delivery es responsabilidad de Sebastián, Orders participa como proveedor:

1. Delivery recibe claim de órdenes.
2. Delivery → Orders:
   `GET /api/internal/orders?ids=1,2`
3. Orders devuelve órdenes y estado.
4. Delivery utiliza información para crear/gestionar rutas.

Por lo tanto, el endpoint interno de Orders es una dependencia crítica para Delivery.

---

# 14. Orden general de migración

El plan establece:

## Fase 1 — Endpoints internos

Todos los responsables trabajan en paralelo.

Duración estimada: 2 días.

Felipe:

`GET /api/internal/orders?ids=...`

Dependencias externas que Felipe necesita:

- Nicolás → Auth endpoints
- Javier → Catalog endpoints
- Sebastián → Delivery endpoints

Al final deben existir y estar testeados los endpoints internos.

---

## Fase 2 — Reemplazar adapters

Dependencia: Fase 1.

Duración estimada: 3 días.

Felipe:

- reemplazar `SupabaseRestCatalogAdapter` por HTTP;
- reemplazar acceso a users por Auth HTTP;
- reemplazar acceso a delivery por Delivery HTTP;
- reemplazar escritura de delivery_routes por Delivery HTTP.

La BD todavía puede ser compartida durante esta fase, pero ningún servicio debe acceder a tablas ajenas.

---

## Fase 3 — Bases de datos independientes

Duración estimada: 1 día.

Crear BD propia de cada servicio.

Para Orders:

- nueva Supabase;
- tablas Orders;
- seed;
- `.env`;
- conexión propia.

---

## Fase 4 — Validación E2E

Duración estimada: 1 día.

Levantar los 4 servicios con BDs independientes.

Validar:

`registro → login → catálogo → crear orden → asignar repartidor → actualizar estado de ruta`

Y verificar que no existan queries fallidas por tablas faltantes.

---

# 15. Tests mínimos de Orders

Total mínimo: 14.

## 15.1 Tests unitarios — 6

### CreateOrderTest

1. Productos válidos:
   - calcula correctamente subtotal;
   - delivery_fee;
   - total.

2. Producto no disponible:
   - lanza excepción de dominio.

3. Lista de productos vacía:
   - lanza excepción.

### UpdateOrderStatusTest

4. Transición válida:
   - por ejemplo `Nuevo pedido → Preparando`.

5. Transición inválida:
   - lanza excepción.

### ClaimOrdersTest

6. Asignación:
   - actualiza `delivery_id`;
   - actualiza status.

---

## 15.2 Tests HTTP Clients — 6

### CatalogHttpClientAdapter

7. IDs válidos:
   - retorna productos;
   - incluye precio.

8. Catalog caído/timeout:
   - excepción manejable;
   - no convertir automáticamente en 500 genérico.

### AuthHttpClientAdapter

9. Usuario válido:
   - retorna datos.

10. Usuario inexistente:
   - retorna `Optional.empty`;
   - no excepción inesperada.

### DeliveryHttpClientAdapter

11. Obtener repartidor:
   - retorna información.

12. Crear ruta:
   - retorna HTTP 201.

Se recomienda utilizar:

- MockRestServiceServer
- o WireMock

para simular servicios externos.

---

## 15.3 Tests endpoint interno — 2

13. `GET /api/internal/orders?ids=1,2,3`
   - retorna órdenes existentes.

14. `GET /api/internal/orders?ids=999`
   - retorna array vacío.

---

# 16. Criterios de término

Orders no se considera migrado hasta cumplir:

- no existen accesos directos SQL/REST Supabase a tablas externas;
- `orders`, `order_items` y `client` son propias de Orders;
- Orders tiene BD independiente;
- `.env` apunta a su BD propia;
- existen los HTTP clients necesarios;
- `/api/internal/orders` funciona;
- endpoints internos están protegidos;
- errores siguen formato estándar;
- existe seed independiente;
- tests mínimos pasan;
- contratos JSON coinciden con lo especificado;
- flujo E2E funciona con las 4 BDs separadas.

---

# 17. Regla para el análisis del código

Este documento representa el estado/requisito definido por el plan de migración.

Al analizar el repositorio real:

1. No asumir que algo está implementado porque aparece aquí.
2. No asumir que algo falta sin buscarlo.
3. Comparar código real contra este documento.
4. Identificar diferencias.
5. Marcar incertidumbres como `REQUIERE CONFIRMACIÓN`.
6. No modificar código durante el diagnóstico.
7. No modificar Auth, Catalog ni Delivery.
8. Orders debe consumir las APIs externas, no implementar cambios dentro de los otros servicios.

El objetivo del diagnóstico es determinar:

`PLAN DE MIGRACIÓN + CÓDIGO REAL → BRECHAS → DEPENDENCIAS → PLAN DE IMPLEMENTACIÓN`


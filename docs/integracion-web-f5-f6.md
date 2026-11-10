# Guía de integración para web y app: eliminar cuenta (F5) y alta de tiendas y repartidores (F6)

**Para:** equipo de la web (Sebastián) y de la app móvil (Javier).
**De:** Felipe (backend).
**Qué es esto:** todo lo que necesitan para conectar las pantallas con los servicios nuevos, sin leer el código del backend.
**Estado:** el backend está implementado y probado de punta a punta a través del gateway (50 de 50 comprobaciones). Las pantallas son de ustedes.

---

## 0. Reglas generales (aplican a todo lo de abajo)

- **Dirección base:** siempre el gateway (en producción y FloCI), nunca los servicios directos. Las rutas de esta guía ya están enrutadas.
- **Sesión:** todas las llamadas (salvo las marcadas "pública") llevan `Authorization: Bearer <accessToken>`. El token se obtiene con `POST /auth/login` y dura unos 15 minutos.
- **Método DELETE:** el gateway lo permite en CORS. **PATCH no está permitido** por el gateway; no se usa en esta guía.
- **Cada servicio responde sus errores con una forma distinta.** No intenten un manejador único por forma; usen el **código HTTP** y, cuando exista, el campo indicado:

| Servicio | Forma del error | Dónde está el motivo |
|---|---|---|
| Auth (`/auth/...`) | `{ "code", "service", "traceId", "message" }` | `code` (estable, apto para lógica) y `message` |
| Catalog (`/api/catalog/...`) | `{ "status", "error", "message" }` | `message` |
| Delivery (`/api/delivery/...`) | `{ "error", "message", "timestamp", "status" }` (400, 409) o `{ "code", "service", "traceId", "message" }` (401, 403) | `message` |
| Orders (`/api/orders/...`) | `{ "status", "error", "message" }` | `message` |

- **Los mensajes de error están en español en Auth y en parte de Catalog**, pero no están pensados para mostrarse tal cual al usuario final: muestren sus propios textos según el código HTTP (se sugieren abajo).
- **401** significa "la sesión no sirve" (token ausente, vencido, o cuenta eliminada). La app debe llevar al usuario a iniciar sesión.

### ¿Quién es "administrador"?

**No existe un rol "Admin".** El administrador es el usuario que tiene **a la vez los tres roles `Cliente`, `Restaurante` y `Repartidor`** (como `admin@demo.cl`).

- Para decidir si **mostrar el panel de administración**, miren la lista `roles` de la respuesta de `POST /auth/login` (o del token): si contiene los tres, es administrador.
- Eso solo decide **qué se muestra**. La seguridad real la aplica el backend: cualquier llamada de administración de un usuario que no tenga los tres roles recibe **403**.
- En producción el primer administrador se crea asignando los tres roles con el endpoint interno de Auth desde el servidor (no hay datos de ejemplo en producción). Lo hace el equipo de infraestructura, no la pantalla.

---

## 1. F5 — Eliminar la cuenta propia

Google Play y la Ley 21.719 exigen esta opción. Sirve igual para la app y para la web.

### 1.1 Qué ve y qué hace la persona

1. Entra a "Eliminar mi cuenta" (ajustes o perfil).
2. La pantalla le explica qué pasa (ver textos sugeridos) y le pide su **contraseña**.
3. Confirma. La app llama al endpoint.
4. Según la respuesta: aviso de éxito y cierre de sesión (202), o mensaje de por qué no se puede (409, 401, 429, 503).

**Quien tiene la contraseña de la cuenta puede eliminarla.** No hay otro método de verificación en esta etapa (ver pendientes en la sección 4).

### 1.2 Endpoint

`DELETE /auth/account` — requiere sesión.

Cuerpo:

```json
{ "password": "la-clave-de-la-persona" }
```

| Código | Cuándo | Qué hacer en la pantalla |
|---|---|---|
| **202** (sin cuerpo) | Aceptada. La cuenta queda **inhabilitada al instante** y todas sus sesiones se cierran. El borrado de datos se completa en segundo plano. | Mostrar el aviso de éxito, **borrar el token guardado** y volver a la pantalla de inicio. No intentar nada más con esa sesión. |
| **409** `code = ACCOUNT_HAS_ACTIVE_PROCESSES` | Tiene pedidos en curso (como cliente, como dueño de tienda o como repartidor asignado). **No se cancela nada automáticamente.** | Mostrar el motivo y que cierre o cancele sus pedidos antes. La cuenta sigue funcionando. `message` trae el conteo por papel. |
| **409** `code = ACCOUNT_DELETION_IN_PROGRESS` | Ya había una eliminación en curso. | Mostrar "ya estamos eliminando tu cuenta" y cerrar sesión. |
| **401** `code = AUTH_INVALID_CREDENTIALS` | Contraseña incorrecta. | "La contraseña no es correcta". Dejar reintentar. |
| **401** `code = AUTH_TOKEN_INVALID` | La sesión venció. | Pedir iniciar sesión de nuevo. |
| **400** `code = VALIDATION_ERROR` | Falta la contraseña. | Validar el campo antes de enviar. |
| **429** `code = AUTH_RATE_LIMITED` | Demasiados intentos con contraseña incorrecta. | "Espera unos minutos e inténtalo de nuevo". |
| **503** `code = SERVICE_UNAVAILABLE` | No se pudo comprobar si hay pedidos en curso (otro servicio no responde). **No se tocó la cuenta.** | "No pudimos procesar tu solicitud, inténtalo en unos minutos". |

Ejemplo de 409:

```json
{
  "code": "ACCOUNT_HAS_ACTIVE_PROCESSES",
  "service": "auth-service",
  "traceId": "1a2383f86f184962",
  "message": "No puedes eliminar tu cuenta mientras tengas pedidos en curso (como cliente: 1, como tienda: 0, como repartidor: 0). Cancelalos o espera a que terminen e intenta de nuevo."
}
```

### 1.3 Qué pasa después del 202 (para que la pantalla no prometa de más)

- **Al instante:** la persona no puede iniciar sesión (`POST /auth/login` responde 401), y su token anterior deja de valer también en Orders.
- **En segundo plano (normalmente en segundos):** Auth borra los datos en Orders, Delivery y Catalog y al final borra el usuario. Si un servicio no responde, **se reintenta solo**; no hay nada que la persona o la pantalla deba hacer.
- **Qué se borra:** correo, RUT, nombre, teléfono, foto y contraseña (Auth); direcciones de sus pedidos y el vínculo de su perfil de cliente (Orders); direcciones de sus rutas y su perfil de repartidor (Delivery); el vínculo del dueño con su tienda (Catalog).
- **Qué se conserva:** los pedidos ya terminados se mantienen como registro mínimo (número, estado, montos, fechas, tienda y productos) **sin ningún dato del cliente**. Si es dueño de una tienda, la tienda y su catálogo se conservan, quedan **cerradas** y sin dueño.
- **No se puede deshacer.**

### 1.4 Cómo se ven los pedidos de un cliente eliminado (en las listas de tienda y repartidor)

El cliente "no existe": los campos `clientName`, `clientPhone`, `clientEmail` llegan **vacíos o nulos** y `address` llega **vacía** (`""`). **No hay texto sustituto** ("cliente eliminado"). Si la pantalla quiere mostrar algo, que lo decida la pantalla; el backend no lo manda.

### 1.5 Textos sugeridos

- Título: **Eliminar mi cuenta**.
- Explicación: "Se eliminarán tu perfil y tus datos personales (nombre, correo, teléfono, direcciones). Tus pedidos ya terminados se conservan sin tus datos como registro del comercio. Esta acción no se puede deshacer."
- Bloqueo (409): "Tienes pedidos en curso. Cancélalos o espera a que terminen y vuelve a intentarlo."
- Éxito (202): "Recibimos tu solicitud. Tu cuenta ya está desactivada y tus datos se están eliminando."

### 1.6 Vía web sin iniciar sesión (pendiente de decisión)

El plan pide contemplar una vía web para quien **no puede entrar** a su cuenta. Esa vía **no está implementada**: requeriría confirmar la identidad por correo (como la recuperación de contraseña). Mientras no se decida con asesoría legal, la web ofrece solo la opción con sesión descrita arriba.

---

## 2. F6 — Alta de tiendas y repartidores (panel de administración)

Solo para administradores (ver la definición arriba). Todas las rutas devuelven **403** si el usuario no lo es y **401** si no hay sesión.

### 2.1 El orden de llamadas (lo que debe hacer el panel)

La persona **se registra sola** desde la app (queda como Cliente). El administrador la busca por correo y completa el alta.

**Dar de alta una tienda**

```
1. GET  /auth/admin/users?email=<correo>                      → obtener su id y confirmar que existe
2. PUT  /auth/admin/users/{id}/roles   ["Cliente","Restaurante"]  → darle el rol
3. POST /api/catalog/admin/restaurants {name, address, ownerUserId}  → crear la tienda con ese dueño
```

Después, el dueño **cierra y vuelve a abrir sesión** (el rol viaja en el token) y administra su tienda desde `/api/catalog/my/**`: abrirla (`PUT /api/catalog/my/restaurant/open-status`), definir el horario y cargar productos. **La tienda nace cerrada** hasta que su dueño la abre.

**Dar de alta un repartidor**

```
1. GET  /auth/admin/users?email=<correo>
2. PUT  /auth/admin/users/{id}/roles   ["Cliente","Repartidor"]
3. POST /api/delivery/admin/persons    {userId, vehicle?}
```

Después el repartidor inicia sesión de nuevo y ve los pedidos disponibles.

> Importante: el paso 2 **reemplaza** el conjunto de roles. Si la persona ya tenía `Restaurante` y se le asigna solo `["Cliente","Repartidor"]`, **pierde** `Restaurante`. Para sumar un rol, envíen la lista completa (los que tenía más el nuevo; la respuesta del paso 1 trae los actuales).

### 2.2 Buscar un usuario por correo

`GET /auth/admin/users?email=<correo>`

```json
{ "id": 34, "name": "Ana", "lastName": "Pérez", "email": "ana@correo.cl", "roles": ["Cliente"] }
```

No devuelve datos sensibles (ni RUT, ni teléfono, ni contraseña). Errores: **401** sin sesión, **403** no es administrador (`code = FORBIDDEN`), **404** `code = USER_NOT_FOUND` (el correo no está registrado: pedirle a la persona que se registre primero).

### 2.3 Asignar roles

`PUT /auth/admin/users/{userId}/roles`

```json
{ "roles": ["Cliente", "Restaurante"] }
```

Respuesta 200:

```json
{ "userId": 34, "roles": [ { "id": 1, "name": "Cliente" }, { "id": 2, "name": "Restaurante" } ] }
```

- Roles asignables desde el panel: **`Cliente`, `Restaurante`, `Repartidor`**. `Soporte` **no** se puede asignar por aquí (400).
- Un administrador **no puede cambiar sus propios roles** (403), para no quedarse sin acceso.
- Errores: **400** rol no asignable o lista ausente, **403** no administrador o intento sobre sí mismo, **404** usuario inexistente.
- La acción queda auditada con el administrador como actor.

### 2.4 Crear una tienda

`POST /api/catalog/admin/restaurants`

```json
{ "name": "Mi Tienda", "address": "Calle 1", "ownerUserId": 34 }
```

`address` y `ownerUserId` son opcionales (una tienda puede crearse sin dueño y asignarlo después). Respuesta **201**:

```json
{ "id": 9, "name": "Mi Tienda", "address": "Calle 1", "ownerUserId": 34, "acceptingOrders": false, "open": false }
```

Errores: **400** (`name` obligatorio, `ownerUserId` debe ser positivo), **403**, **409** "El usuario ya tiene asignado otro comercio" (un usuario solo puede tener una tienda).

### 2.5 Asignar o cambiar el dueño de una tienda

`PUT /api/catalog/admin/restaurants/{restaurantId}/owner` con `{ "userId": 60 }` → **200** con la misma forma que arriba. Errores: **403**, **404** (tienda inexistente), **409** (ese usuario ya tiene otra tienda).

### 2.6 Listar tiendas con su dueño

`GET /api/catalog/admin/restaurants` → **200** con una lista de la forma anterior (incluye `ownerUserId`, que `/catalog/restaurants` público no expone; `ownerUserId` puede venir nulo).

### 2.7 Crear el perfil de repartidor

`POST /api/delivery/admin/persons`

```json
{ "userId": 35, "vehicle": "MOTO" }
```

`vehicle` es opcional: `MOTO`, `AUTO`, `BICICLETA` o `PIE`. Respuesta **201**:

```json
{ "success": true, "message": "Delivery person profile created",
  "data": { "id": 9, "userId": "35", "vehicle": "MOTO", "createdAt": "2026-11-06T16:33:40.564Z" } }
```

(Ojo: aquí `userId` viene como **texto**.) Errores: **400** (`userId` ausente o no positivo), **403**, **409** (ese usuario ya tiene perfil de repartidor). Antes hay que haberle asignado el rol `Repartidor` (sección 2.3).

### 2.8 Textos y avisos sugeridos para el panel

- Correo no registrado (404): "Esa persona aún no se registró. Pídele que cree su cuenta en la app y vuelve a buscarla."
- Dueño con tienda (409): "Esa persona ya es dueña de otra tienda."
- Perfil repetido (409): "Esa persona ya es repartidora."
- Tienda creada: recordar que "queda cerrada hasta que su dueño la abra".

---

## 3. Lista de verificación para las pruebas de las pantallas

Con cuentas creadas para la prueba (**nunca** las cuentas demo; eliminar `cliente@demo.cl` rompería los datos de todos).

**Eliminar cuenta**
- [ ] Con un pedido en curso: el botón muestra el aviso y la cuenta sigue funcionando.
- [ ] Sin pedidos en curso: tras confirmar, la sesión se cierra y no se puede volver a entrar con ese correo.
- [ ] Contraseña incorrecta: mensaje claro y se puede reintentar.
- [ ] Un repartidor con un pedido asignado y un dueño con pedidos pendientes reciben el bloqueo.
- [ ] Las listas de tienda y repartidor muestran el pedido del cliente eliminado sin nombre ni dirección.

**Panel de administración**
- [ ] Un usuario sin los tres roles no ve el panel y, si llama a las rutas, recibe 403.
- [ ] Buscar por correo, dar el rol y crear la tienda: la tienda aparece en el catálogo público **solo cuando su dueño la abre**.
- [ ] Un repartidor dado de alta ve los pedidos disponibles tras iniciar sesión de nuevo.
- [ ] Errores 404 y 409 se muestran con textos propios.

El mismo recorrido está automatizado por API en `qa/e2e/f23_f24.py` (ver `qa/e2e/README.md`).

---

## 4. Lo que está pendiente (no lo asuman resuelto)

- **Solicitud web sin sesión** (método de verificación por correo): sin decidir, no implementado.
- **Evasión** (quien eliminó su cuenta por una sanción y se registra de nuevo): sin decidir con asesoría legal.
- **Plazos y excepciones de conservación** (contabilidad, SII): los define asesoría legal.
- **Aviso al usuario cuando termina el borrado en segundo plano:** no existe (no hay notificaciones push todavía). La pantalla solo informa el 202.
- **Pantallas:** la opción en la app, la vista web de eliminación y el panel de administración son de ustedes.

---

## 5. Referencia técnica

- Contratos completos: `services/auth-service/openapi.yaml`, `services/catalog-service/openapi.yaml`, `services/delivery-service/openapi.yaml` y `services/orders-service/openapi.yaml`.
- Flujo interno y decisiones: `odd/tasks/account-deletion.md` y `odd/tasks/admin-onboarding.md`.
- Los endpoints `/api/internal/**` son solo entre servicios (clave `X-Internal-Api-Key`) y **no están expuestos por el gateway**: no los usen desde la web ni la app.

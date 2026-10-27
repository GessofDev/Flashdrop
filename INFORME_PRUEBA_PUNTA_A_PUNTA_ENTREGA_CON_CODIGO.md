# Informe: Prueba de punta a punta de la entrega con código

**Para:** Nicolás (documentar y repetir el flujo) y Felipe (validar la entrega) · **Fecha de la prueba:** 2026-10-26 (actualizado 2026-10-27)
**Documento hermano:** `INFORME_IMPLEMENTACION_ENTREGA_CON_CODIGO.md` (qué se hizo, endpoints, despliegue y pruebas automáticas)
**Rama probada:** `feat/delivery-code-verification` (sobre `origin/main` `1cfd0ba`)
**Qué es esto:** el recorrido completo con los servicios **reales** levantados (Auth, Catalog, Orders y Delivery) y bases de datos PostgreSQL reales. No hay mocks en esta prueba.
**Resultado:** **todo lo que se esperaba pasó.** Se encontraron 4 hallazgos (sección 5); el único que afectaba a esta función (el formato del id en el detalle del pedido) **ya está corregido**. Las cosas que esta prueba **no** cubre están en la sección 6 y las consideraciones para el equipo en la sección 7.

---

## 1. Qué se probó y con qué

```text
  curl (hace de app)  ──►  Orders :8083  ──►  Postgres (copia de la base vieja)
                              │  ▲
              claim/estado    ▼  │ validación de token
                          Delivery :8084 ──►  Postgres propio
                              ▲
                  Auth :8081 (tokens) · Catalog :8082 (productos, en memoria)
```

- Se llamó **directo a cada servicio** (sin gateway), como lo haría la app.
- RabbitMQ **no** estaba levantado (los eventos son "mejor esfuerzo": Orders solo deja un aviso en el log).
- Personas de prueba: restaurante (dueño del restaurante 1), dos repartidores, un cliente.

### Ensayo de migración (se hizo primero)
Se copió la base real de pruebas anteriores de Orders (3 pedidos, solo con la migración V1) a un contenedor nuevo y se arrancó Orders contra ella.

| Qué se verificó | Resultado |
|---|---|
| Flyway aplicó `V2` y `V3` sobre los datos existentes | ✅ historial: 1 init, 2 snapshot, 3 delivery code (todas `success`) |
| Los 3 pedidos viejos quedaron en modo `NORMAL`, versión 1, 0 intentos | ✅ |
| Los pedidos viejos se siguen listando y consultando | ✅ |
| La tabla de auditoría se creó con su llave foránea | ✅ |

La base original no se tocó (solo se leyó con `pg_dump`) y el contenedor quedó apagado como estaba.

## 2. Escenarios y resultados

### A. Pedido SEGURO: camino completo (pedido #4)

| # | Paso | Esperado | Resultado |
|---|---|---|---|
| A1 | Cliente crea pedido con `receptionMode: SEGURO` | 201, trae `deliveryCode` | ✅ 201, código de 6 dígitos |
| A2 | Restaurante: Preparando → Listo para retiro | 200 | ✅ |
| A3 | Repartidor toma el pedido (Delivery → Orders) | 201 | ✅ |
| A4 | Repartidor marca "Retirado" | 200 y la ruta en Delivery pasa a "En camino" | ✅ |
| A5 | ¿Quién ve el código? | Solo el cliente | ✅ cliente lo ve; repartidor y restaurante: el campo no aparece |
| A6 | Otro repartidor consulta el pedido | 403 | ✅ |
| A7 | Los listados | Traen `receptionMode`, nunca el código | ✅ |
| A8 | La base de datos | No guarda el código | ✅ no existe ninguna columna con el código |
| A9 | Código con formato inválido (`12ab`) o ausente | 400 | ✅ |
| A10 | 1º y 2º intento fallido | 422, quedan 2 y 1 | ✅ |
| A11 | 3º intento fallido | 422, `codeRenewed: true`, quedan 3 | ✅ |
| A12 | El cliente ve un código **distinto** al original | Sí | ✅ (`242425` → `406285`) |
| A13 | El código **viejo** ya no sirve | 422 | ✅ |
| A14 | Otro repartidor con el código **correcto** | 403 | ✅ |
| A15 | Cliente o restaurante llamando a `/deliver` | 403 | ✅ |
| A16 | `PUT /api/orders/4/status` con "Entregado" | 409 | ✅ |
| A17 | `PUT` de la ruta en Delivery con `ENTREGADO` y con `entregado` | 409 | ✅ ambos |
| A18 | `PUT` de la ruta por un repartidor que no es el dueño | 403 | ✅ |
| A19 | Tras esos intentos, el estado no cambió | Orders "Retirado", ruta "En camino" | ✅ |
| A20 | Código correcto (el nuevo) | 200 | ✅ |
| A21 | Estado final | Orders "Entregado"; ruta en Delivery "Entregado" (con ese formato exacto) | ✅ |
| A22 | Repetir la entrega con el mismo código | 409 (un solo uso) | ✅ |
| A23 | El cliente ve el código tras la entrega | No | ✅ ya no aparece |
| A24 | Auditoría | 4 fallos y 1 acierto, sin el código escrito | ✅ |

### B. Pedido NORMAL (pedidos #5 y creado sin el campo)

| # | Paso | Esperado | Resultado |
|---|---|---|---|
| B1 | Crear pedido **sin** `receptionMode` (como la app actual) | NORMAL, sin código en la respuesta | ✅ |
| B2 | Modo inválido (`express`) | 400 | ✅ |
| B3 | Modo en minúsculas (`seguro`) | Se acepta | ✅ |
| B4 | 5 códigos incorrectos seguidos en modo normal | 422 cada vez, **sin contar intentos** | ✅ intentos = 0 |
| B5 | Código `000000` | 200 → Entregado, ruta "Entregado" | ✅ |

### C. Bloqueo secuencial (pedido #6)

| Intento fallido | Esperado | Resultado |
|---|---|---|
| 1 / 2 | 422, quedan 2 / 1 | ✅ |
| 3 | 422, código renovado, quedan 3 | ✅ |
| 4 / 5 | 422, quedan 2 / 1 | ✅ |
| 6 | **423** bloqueado | ✅ |
| 7 | 423 | ✅ |

### D. Pedido SEGURO: `000000` no sirve y ráfaga simultánea (pedido #7)

| Qué | Esperado | Resultado |
|---|---|---|
| `000000` en modo seguro | 422 (no es el código) | ✅ |
| 7 intentos malos **al mismo tiempo** | Respuestas: 4 × 422 y 3 × 423, en total 6 intentos registrados | ✅ exacto: `5 × 422` (contando el primero) y `3 × 423`; 6 filas de auditoría |
| Estado final | Versión 2, 3 intentos, sigue "Retirado" | ✅ |
| Con el pedido bloqueado, código correcto del cliente | 423 | ✅ |
| Con el pedido bloqueado, código original | 423 | ✅ |

### E. Casos de borde

| Caso | Esperado | Resultado |
|---|---|---|
| Pedido inexistente | 404 | ✅ |
| Sin token | 401 | ✅ |
| UUID en la ruta en vez del número | Se acepta | ✅ |
| Entregar un pedido que aún no fue retirado | 409 y no se descuenta intento | ✅ (intentos = 0) |

### Estado final en las bases (resumen)

| Pedido | Modo | Estado | Código (versión / intentos) | Ruta en Delivery |
|---|---|---|---|---|
| 1–3 (viejos) | NORMAL | sin cambios | 1 / 0 | — |
| 4 | SEGURO | **Entregado** | 2 / 1, verificado | Entregado |
| 5 | NORMAL | **Entregado** | 1 / 0, verificado | Entregado |
| 6 | SEGURO | Retirado (**bloqueado**) | 2 / 3 | En camino |
| 7 | SEGURO | Retirado (**bloqueado**) | 2 / 3 | En camino |
| 8 | SEGURO | Listo para retiro | 1 / 0 | Asignado |

Auditoría total: 2 aciertos y 16 fallos. Los 5 errores del modo normal no se registran, como corresponde.

## 3. Lo que esto confirma sobre el diseño

1. **El único camino a "Entregado" es el código.** Se intentó por Orders (`PUT /status`) y por Delivery (`PUT` de la ruta): ambos 409.
2. **El conteo de intentos aguanta la simultaneidad** con el servidor real, no solo en la prueba automática.
3. **Orders y Delivery quedan sincronizados**: cuando Orders avisa `ENTREGADO`, Delivery lo guarda como "Entregado". Esto confirma en vivo la normalización que se agregó en Delivery.
4. **Los pedidos antiguos no se rompen** con la migración.

## 4. Cómo repetir la prueba (para Nicolás)

Estos pasos tienen trampas que descubrí al montarlo. Están todas incluidas.

**Bases (3 contenedores PostgreSQL 15 con Docker):** una para Auth, una para Orders, una para Delivery (`delivery_db`). Con usuario `postgres`.

**Arranque** (cada uno en su terminal; la clave interna debe ser la misma en todos):

```bash
# Auth (desde services/)
DB_URL=jdbc:postgresql://localhost:6432/flashdrop_auth DB_USERNAME=postgres DB_PASSWORD=pg \
JWT_ALLOW_EPHEMERAL=true INTERNAL_API_KEY=e2e-key ./gradlew :auth-service:bootRun

# Catalog (desde services/catalog-service/ — tiene su propio Gradle, NO está en el de services/)
SPRING_PROFILES_ACTIVE=local INTERNAL_API_KEY=e2e-key ./gradlew bootRun

# Delivery (desde services/)
SPRING_PROFILES_ACTIVE=delivery DELIVERY_DB_HOST=localhost DELIVERY_DB_PORT=6434 \
DELIVERY_DB_NAME=delivery_db DELIVERY_DB_USER=postgres DELIVERY_DB_PASSWORD=pg \
AUTH_SERVICE_JWKS_URI=http://localhost:8081/auth/.well-known/jwks.json \
INTERNAL_API_KEY=e2e-key ORDERS_SERVICE_URL=http://localhost:8083 ./gradlew :delivery-service:bootRun

# Orders (desde services/orders-service/)
DB_URL=jdbc:postgresql://localhost:6433/flashdrop_orders DB_USERNAME=postgres DB_PASSWORD=pg \
AUTH_SERVICE_URL=http://localhost:8081 CATALOG_SERVICE_URL=http://localhost:8082 \
DELIVERY_SERVICE_URL=http://localhost:8084 INTERNAL_API_KEY=e2e-key \
DELIVERY_CODE_SECRET=cualquier-clave-larga FLYWAY_ENABLED=true ./mvnw spring-boot:run
```

**Usuarios y roles (trampas):**
1. La base de Auth viene **sin roles**. Registrar un usuario da error 500 hasta ejecutar:
   `insert into roles(name,route) values ('Cliente','/client'),('Restaurante','/restaurant'),('Repartidor','/delivery');`
2. El **teléfono** de cada usuario debe ser distinto (es único); si no, responde 409 "ya existe".
3. Todos se registran como "Cliente". Los demás roles se asignan en la tabla `user_has_roles`.
4. El **restaurante 1 de Catalog es del usuario con id 1**: el primer usuario que registres debe ser el restaurante. Los productos 1 y 2 son de ese restaurante.
5. El repartidor debe crear su perfil: `POST /api/delivery/persons` con su token.
6. Login: `POST /auth/login` con `{"login": "...", "password": "..."}` (el campo se llama `login`, no `email`). El token dura 15 minutos.

**Recorrido mínimo:**

```bash
# 1 cliente crea pedido seguro (guardar deliveryCode)
POST :8083/api/orders  {"address":"Av. Test","paymentMethod":"Tarjeta","receptionMode":"SEGURO","items":[{"productId":1,"quantity":1}]}
# 2 restaurante
PUT  :8083/api/orders/{n}/status {"status":"Preparando"}   y luego   {"status":"Listo para retiro"}
# 3 repartidor toma y retira
POST :8084/api/delivery/claim    {"orderIds":[n]}
PUT  :8083/api/orders/{n}/status {"status":"Retirado"}
# 4 repartidor entrega con el código que le dicta el cliente
POST :8083/api/orders/{n}/deliver {"code":"123456"}
```

Notas: durante la prueba `GET /api/orders/{id}` pedía el **UUID** (`00000000-0000-0000-0000-00000000000N`) mientras el resto aceptaba el número `N`. **Ya está corregido** (ver hallazgo 1): ahora acepta ambos. Un repartidor con pedidos activos no puede tomar otro (409): es una regla existente.

## 5. Hallazgos durante la prueba

| # | Hallazgo | Estado |
|---|---|---|
| 1 | `GET /api/orders/{id}` solo aceptaba el UUID; `PUT /status` y `/deliver` aceptan también el número. | **CORREGIDO** en esta rama (2026-10-27): el detalle ahora acepta número o UUID, igual que los otros dos. Cambio mínimo y compatible: las respuestas siguen devolviendo el mismo `id`. Justificación: la convención del proyecto es "wire = Long, dominio = UUID" (`HANDOFF_ORDERS_FLASHDROP.md` §9.1) y los commits `ea99d1a` y `797804e` ya habían movido claim, pedidos disponibles y estado al número; el detalle era el único que faltaba. Prueba: `OrderControllerDetailIdFormatTest` (Orders: 304 + 16 pruebas verdes). **Nota:** el arreglo se verificó con pruebas automáticas; no se repitió en vivo contra el servidor levantado (conviene que Nicolás lo confirme: `GET /api/orders/{número}` con el token del cliente). |
| 2 | Orders pide `/api/internal/restaurants/{id}` a Catalog para enriquecer el detalle; el Catalog en memoria no tiene esa ruta y Orders deja errores en el log. | Anterior a este cambio. La respuesta sale bien (sin datos del restaurante): es ruido, no un fallo. |
| 3 | La base de Auth del `main` no trae la semilla de roles. | Anterior a este cambio. Una instalación nueva no puede registrar usuarios hasta cargarla (ver sección 4). |
| 4 | Catalog no está en el Gradle de `services/` (aunque el `CLAUDE.md` del repo dice que sí). | Anterior a este cambio. Solo documentación desactualizada. |

## 6. Pendientes de prueba (lo que esta prueba NO cubre)

| Pendiente | Por qué importa | Quién |
|---|---|---|
| **Repetir y documentar este flujo** con la guía de la sección 4 | Es la validación formal de la entrega | Nicolás |
| **Pasando por el gateway** | Se llamó directo a los servicios. Falta confirmar que el gateway deja pasar `POST /api/orders/{id}/deliver` (el CORS del gateway no admite PATCH, pero `/deliver` es POST, así que debería funcionar) | Nicolás |
| **Delivery caído en el momento de entregar** | Por el código, si Delivery no responde, Orders deja el pedido "Entregado" igual y solo registra un aviso: la ruta quedaría desactualizada. No se probó. Es el comportamiento que ya tenía el cambio de estado. Conviene decidir si se tolera o se reconcilia | Nicolás / decisión del equipo |
| **RabbitMQ** | No estaba levantado; no se probaron los eventos | Quien tenga el ambiente |
| **Secreto real de FloCI** | Se usó una clave de prueba | Quien despliegue |
| **Migración sobre la base real de FloCI** | Se ensayó sobre una copia de la base de pruebas locales. Falta repetirla sobre la de FloCI antes de desplegar (y recordar que allí Flyway está apagado) | Quien despliegue |
| **Las apps móviles** | Esta prueba hizo de app con `curl` | Equipo de la app |

## 7. Consideraciones para el resto del equipo

El detalle completo está en `INFORME_IMPLEMENTACION_ENTREGA_CON_CODIGO.md` (secciones 9 y 11). Lo que más conviene tener presente al probar:

1. **Ids del pedido.** Crear pedido, listado y detalle devuelven el `id` como **UUID**; Delivery y "pedidos disponibles" entregan el **número**. Todos los endpoints de Orders aceptan ambos, así que cada rol puede usar el id que recibió. Unificar las respuestas al número sería un cambio de contrato (app + Sebastián): queda como decisión del equipo.
2. **App Android del repartidor.** Hoy marca "Entregado" con `PUT /api/delivery/routes/{id}/status`, que ahora responde 409. Debe pasar a `POST /api/orders/{id}/deliver` **antes** de desplegar Delivery.
3. **Despliegue de Orders.** Aplicar `V3` a mano (Flyway está apagado en FloCI) y crear el secreto `flashdrop/delivery-code-secret` antes de desplegar.
4. **Delivery no tiene CI** y tiene 5 pruebas que ya fallaban en `main` (usuario semilla "U1" borrado por la migración `V6` y una que espera que falte `INTERNAL_API_KEY`). Se verificó en una copia limpia de `main`: no son de este cambio.
5. **Pedido bloqueado.** Hoy no hay herramienta de soporte para destrabarlo.

## 8. Cierre

El desarrollo de esta función **está terminado** (Orders y Delivery, con pruebas automáticas y esta prueba de punta a punta) y cumple el plan aprobado en todo lo que se puede verificar sin las apps ni el ambiente de FloCI. Lo que sigue no es desarrollo: es que Nicolás repita y documente el flujo con la guía de la sección 4 (idealmente también por el gateway), y resolver los puntos de las secciones 6 y 7 antes del despliegue. El commit y el PR quedan en pausa hasta esa validación.

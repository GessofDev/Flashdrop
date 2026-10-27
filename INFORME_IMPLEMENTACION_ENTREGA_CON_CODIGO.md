# Informe de implementación: Entrega con código de verificación

**Para:** Felipe y el equipo (Orders, Delivery, app móvil) · **Fecha:** 2026-10-23 (actualizado 2026-10-27)
**Rama:** `feat/delivery-code-verification` (creada desde `origin/main`, commit `1cfd0ba`)
**Estado:** desarrollo terminado y probado (pruebas automáticas + prueba de punta a punta con servicios reales). **Sin commit ni push** (en pausa hasta que Nicolás valide el flujo).
**Documentos relacionados:** el plan `INFORME_ENTREGA_CON_CODIGO_DELIVERY_ORDERS.md` y la prueba de punta a punta `INFORME_PRUEBA_PUNTA_A_PUNTA_ENTREGA_CON_CODIGO.md`

---

## 1. En una frase

Para marcar un pedido como "Entregado", el repartidor tiene que escribir un **código de 6 números** que solo el cliente conoce. Si no lo tiene, el pedido no se entrega.

## 2. Qué cambió para cada persona

| Quién | Antes | Ahora |
|---|---|---|
| **Cliente** | No tenía forma de saber si la entrega era real | Al comprar elige "modo seguro" y recibe un código. Solo entrega ese código cuando tiene su pedido en la mano. Puede volver a ver el código en el detalle del pedido si se le olvida. |
| **Repartidor** | Apretaba un botón y el pedido quedaba "Entregado" | Debe escribir el código que le dicta el cliente. No puede ver el código. |
| **Restaurante** | — | Sin cambios. Tampoco puede ver el código. |
| **Soporte** | — | Recibe los pedidos que quedan bloqueados por demasiados intentos fallidos. |

## 3. Los dos modos de recepción

El cliente elige al hacer el pedido y **no puede cambiarlo después** (la app debe avisarlo).

| Modo | Para qué sirve | Código |
|---|---|---|
| **SEGURO** | Solo se entrega a quien tenga el código. Si el cliente no lo tiene (batería, no está), el pedido no se entrega. | Único por pedido, de 6 dígitos |
| **NORMAL** (por defecto) | El pedido puede quedar en recepción o con otra persona. | Siempre `000000`. La app del repartidor lo escribe sola. |

Si la app no envía ningún modo, se asume NORMAL. Así la app actual no se rompe.

**Por qué el modo normal también usa código:** para tener un solo camino en el sistema. Todos los pedidos se entregan igual (con código); la única diferencia es que en modo normal el código es público.

## 4. El flujo completo

```text
CLIENTE                    ORDERS                         REPARTIDOR              DELIVERY
   │                          │                               │                      │
   │ 1. Crea pedido           │                               │                      │
   │    receptionMode=SEGURO  │                               │                      │
   │ ───────────────────────► │                               │                      │
   │ ◄─────────────────────── │                               │                      │
   │   recibe deliveryCode    │                               │                      │
   │   "482913"               │                               │                      │
   │                          │ 2. Restaurante prepara...     │                      │
   │                          │    Repartidor toma y retira   │                      │
   │                          │ ◄──────────────────────────── │                      │
   │                          │      estado = Retirado        │                      │
   │                          │                               │                      │
   │ 3. Llega el pedido.      │                               │                      │
   │    Cliente dicta         │                               │                      │
   │    "482913" en voz alta ─┼─────────────────────────────► │                      │
   │                          │                               │                      │
   │                          │ 4. POST /api/orders/{id}/deliver                     │
   │                          │    { "code": "482913" }       │                      │
   │                          │ ◄──────────────────────────── │                      │
   │                          │                               │                      │
   │                          │ 5. Orders compara el código.  │                      │
   │                          │    Si coincide:               │                      │
   │                          │    - pedido = Entregado       │                      │
   │                          │    - avisa a Delivery ──────────────────────────────►│
   │                          │      (ruta = Entregado)       │                      │
   │                          │ ──► 200 OK ─────────────────► │                      │
```

Si el código **no** coincide, ocurre lo del punto 5.

## 5. Qué pasa cuando el código es incorrecto

Cada código permite **3 intentos**. Un pedido puede tener **2 códigos** en total (6 intentos máximo).

| Intento fallido | Qué responde el sistema | Qué ve el cliente |
|---|---|---|
| 1º | `422` "Código incorrecto", quedan 2 intentos | Nada nuevo |
| 2º | `422`, queda 1 intento | Nada nuevo |
| 3º | `422`, `codeRenewed: true`: el código se anuló y se creó uno nuevo | En su pedido aparece un **código nuevo** |
| 4º, 5º | `422`, quedan 2 y 1 intentos | — |
| 6º | `423` **Bloqueado** | El pedido ya no se puede entregar; hay que ir a soporte |
| Cualquier intento posterior | `423` Bloqueado (aunque el código sea el correcto) | — |

**Por qué hay un tope:** sin él, un repartidor podría probar números hasta acertar. Con 6 dígitos y solo 6 intentos, la probabilidad de acertar a ciegas es de alrededor de 6 en 1.000.000.

**Detalle importante:** los intentos los cuenta el servidor (bloqueando la fila del pedido en la base de datos). Aunque alguien dispare muchos intentos al mismo tiempo, no puede pasar del límite. Esto está probado con PostgreSQL real, tanto en una prueba automática como contra el servidor levantado (ver sección 10 y el informe de punta a punta).

## 6. Endpoints

### 6.1 Nuevo: confirmar la entrega

`POST /api/orders/{id}/deliver` — solo rol **Repartidor**. El `{id}` es el número del pedido (o el UUID antiguo; ver 11.1).

```http
POST /api/orders/501/deliver
Authorization: Bearer <token del repartidor>
Content-Type: application/json

{ "code": "482913" }
```

| Código HTTP | Cuándo | Cuerpo |
|---|---|---|
| **200** | Código correcto: pedido "Entregado" | `{ "success": true, "message": "Pedido entregado" }` |
| **400** | Falta el código o no tiene 6 dígitos | `{ "status":400, "error":"BAD_REQUEST", ... }` |
| **403** | No es repartidor, el pedido es de otro repartidor, o el repartidor es el mismo cliente del pedido | `{ "status":403, "error":"FORBIDDEN", ... }` |
| **404** | El pedido no existe | `{ "status":404, "error":"NOT_FOUND", ... }` |
| **409** | El pedido no está "Retirado" (aún no retirado, o ya entregado) | `{ "status":409, "error":"CONFLICT", ... }` |
| **422** | Código incorrecto | ver abajo |
| **423** | Pedido bloqueado por intentos agotados | ver abajo |

```json
// 422 - código incorrecto
{ "status": 422, "error": "VALIDATION_ERROR", "message": "Codigo incorrecto",
  "remainingAttempts": 2, "codeRenewed": false }

// 422 - tercer fallo: el código se renovó
{ "status": 422, "error": "VALIDATION_ERROR",
  "message": "Codigo incorrecto. Se agotaron los intentos: se genero un codigo nuevo para el cliente",
  "remainingAttempts": 3, "codeRenewed": true }

// 423 - bloqueado
{ "status": 423, "error": "LOCKED",
  "message": "Entrega bloqueada por intentos agotados. Contacta a soporte",
  "remainingAttempts": 0, "codeRenewed": false }
```

**Para la app del repartidor:** en modo NORMAL se envía `"code": "000000"` sin preguntarle nada al repartidor. En modo SEGURO se muestra un campo para que escriba el código.

### 6.2 Cambiado: crear pedido

`POST /api/orders` ahora acepta un campo opcional `receptionMode`.

```json
{ "address": "Av. Providencia 1200", "paymentMethod": "Tarjeta",
  "receptionMode": "SEGURO",
  "items": [ { "productId": 8, "quantity": 2 } ] }
```

Respuesta (el código solo viene en modo SEGURO):

```json
{ "success": true, "message": "Pedido creado",
  "data": { "id": "...", "total": 4500,
            "receptionMode": "SEGURO", "deliveryCode": "482913" } }
```

Un valor distinto de `NORMAL` o `SEGURO` responde `400`.

### 6.3 Cambiado: detalle del pedido

`GET /api/orders/{id}` (el `{id}` puede ser el número o el UUID) ahora incluye `receptionMode`, y `deliveryCode` **solo** si se cumplen las tres condiciones:

1. Quien consulta es el **cliente dueño** del pedido.
2. El pedido es modo **SEGURO**.
3. El pedido **no está** "Entregado".

Para el repartidor o el restaurante el campo `deliveryCode` simplemente no aparece. Los listados (`GET /api/orders`) y los pedidos disponibles (`/available-for-delivery`) traen `receptionMode` pero nunca el código.

### 6.4 Cambiado: actualizar estado de un pedido

`PUT /api/orders/{id}/status` con `"status": "Entregado"` ahora responde **409**. Los demás estados funcionan igual. "Entregado" solo se alcanza con `/deliver`.

### 6.5 Cambiado en Delivery

| Endpoint | Cambio |
|---|---|
| `PUT /api/delivery/routes/{id}/status` (y `/delivery/...`) | Con `ENTREGADO` responde **409**. Los otros estados (`EN_CAMINO`, `RETIRAR_PEDIDO`, etc.) siguen igual. |
| `PATCH /api/internal/routes/{id}/status` y `/order/{orderId}/status` | Ahora guardan siempre el estado con el mismo formato ("Entregado", no "ENTREGADO"). Siguen aceptando "Entregado". Es el camino por el que Orders le avisa a Delivery. |

## 7. Cómo funciona por dentro (resumen técnico)

- **El código no se guarda.** Se calcula cada vez con una fórmula (HMAC-SHA256) a partir del número del pedido, la "versión" del código (1 o 2) y una clave secreta del servidor. Si alguien robara la base de datos, no encontraría los códigos. Al "renovar" un código solo sube la versión.
- **En modo SEGURO nunca sale `000000`**, para que no se confunda con el código público.
- **Base de datos (migración `V3__delivery_code.sql`):** columnas nuevas en `orders` (`reception_mode`, `delivery_code_version`, `delivery_code_attempts`, `delivery_code_verified_at`) y una tabla de auditoría `order_delivery_code_attempts` que registra cada intento (acierto o fallo) **sin guardar el código escrito**. Los pedidos antiguos quedan como NORMAL.
- **Un intento fallido siempre queda guardado.** El caso de uso devuelve un resultado en lugar de lanzar un error, porque un error desharía el conteo de intentos.
- **Comparación segura:** el código se compara en tiempo constante, para no filtrar información por el tiempo de respuesta.

Archivos principales:

| Servicio | Archivo | Qué hace |
|---|---|---|
| Orders | `domain/model/Order.java`, `ReceptionMode.java` | Reglas de intentos (3 por código, máx. 2 códigos) |
| Orders | `application/usecase/VerifyDeliveryCodeUseCase.java` | La entrega con código |
| Orders | `infrastructure/adapter/outbound/code/HmacDeliveryCodeAdapter.java` | Calcula el código |
| Orders | `infrastructure/api/OrderController.java` | Endpoint `/deliver` y campos nuevos |
| Orders | `application/usecase/UpdateOrderStatusUseCase.java` | Rechaza "Entregado" por `PUT /status` |
| Orders | `application/usecase/GetOrderDetailUseCase.java` | Muestra el código solo al cliente dueño |
| Orders | `db/migration/V3__delivery_code.sql` | Cambios en la base de datos |
| Delivery | `UpdateRouteStatusUseCaseImpl.java`, `RouteDeliveryRequiresCodeException.java` | Rechaza "Entregado" por la vía pública |
| Delivery | `InternalRoutesController.java` | Normaliza el estado interno |
| Infra | `orders-service.dev.json`, `docker-compose.stack.yml`, `env.stack.template`, `INFRASTRUCTURE.md` | Variable `DELIVERY_CODE_SECRET` |
| Docs | `services/orders-service/openapi.yaml` | Contrato actualizado |

## 8. Estado del desarrollo

**El desarrollo está terminado.** Todo lo que corresponde a código, pruebas automáticas, migración, contrato y configuración de este cambio es nuestro y está hecho. Lo que queda (sección 9 en adelante) depende de otras personas o de ambientes que todavía no tenemos a mano.

| Parte | Estado |
|---|---|
| Orders: modo de recepción, código, `POST /deliver`, intentos, bloqueo, auditoría | ✅ hecho |
| Orders: migración `V3` (probada en base vacía y sobre datos existentes) | ✅ hecho |
| Orders: `PUT /status` ya no permite "Entregado"; el cliente ve su código | ✅ hecho |
| Orders: `GET /api/orders/{id}` acepta número o UUID | ✅ hecho (ver 11.1) |
| Delivery: rechaza "Entregado" por la vía pública y normaliza el estado interno | ✅ hecho |
| Contrato (`openapi.yaml` de Orders) y configuración (`DELIVERY_CODE_SECRET`) | ✅ hecho |
| Pruebas automáticas (Orders 304 + 16 de integración; Delivery 205 de 210, ver 10.2) | ✅ hecho |
| Prueba de punta a punta con servicios reales | ✅ hecha (`INFORME_PRUEBA_PUNTA_A_PUNTA_ENTREGA_CON_CODIGO.md`) |
| Commit y PR | ⏸ en pausa hasta que Nicolás valide el flujo |

## 9. Antes de desplegar (pendientes de otras personas o ambientes)

Están ordenados por urgencia. Cada uno explica el porqué.

### 9.1 Aplicar la migración `V3` a mano en la base de Orders de FloCI
- **Qué:** ejecutar `V3__delivery_code.sql` sobre la base `flashdrop_orders` en FloCI.
- **Por qué:** la configuración de Orders en FloCI tiene `FLYWAY_ENABLED=false`, o sea, las migraciones **no se aplican solas**. Como el código nuevo ya espera las columnas nuevas, si se despliega sin la migración **Orders no podrá leer ningún pedido**.
- **Para qué:** que la base tenga dónde guardar el modo, los intentos y la auditoría.
- **Ya probado:** en local, Flyway aplicó `V3` sobre una copia de datos reales de pruebas (los pedidos viejos quedaron en NORMAL). Falta repetirlo sobre la base de FloCI.
- **Orden:** primero la migración, después el despliegue de Orders.

### 9.2 Crear el secreto `flashdrop/delivery-code-secret`
- **Qué:** crear ese secreto en Secrets Manager de FloCI (una clave larga y aleatoria).
- **Por qué:** es la "llave" con la que se calculan los códigos. La task definition de Orders ya la referencia; si el secreto no existe, **la tarea no arranca**.
- **Para qué:** que el modo SEGURO pueda generar códigos. Sin la clave, Orders arranca igual (si el secreto existe pero está vacío) y solo rechaza crear pedidos SEGURO; el modo NORMAL funciona.
- **Ojo:** no cambiar esa clave cuando haya pedidos SEGURO en curso: cambiaría los códigos que los clientes ya tienen.

### 9.3 Adaptar la app Android del repartidor
- **Qué:** que para marcar "Entregado" use `POST /api/orders/{id}/deliver` en vez de `PUT /api/delivery/routes/{id}/status`.
- **Por qué:** el commit `6f9d239` indica que la app Android usa hoy ese `PUT` de Delivery. Cuando se despliegue Delivery, ese botón empezará a recibir **409** y los repartidores no podrán cerrar entregas.
- **Para qué:** que el flujo siga funcionando. Mientras la app no esté lista, conviene **no desplegar** el cambio de Delivery (o desplegarlo junto con la app).

### 9.4 Adaptar la app del cliente
- **Qué:** selector de modo al comprar (con aviso "no se puede cambiar"), y mostrar el `deliveryCode` en el detalle del pedido.
- **Por qué:** sin esto nadie puede elegir el modo SEGURO y nadie vería su código.
- **Para qué:** que la función sea realmente usable. Mientras tanto, todo pedido queda en NORMAL y nada cambia para el cliente.

### 9.5 Orden recomendado de despliegue
1. Crear el secreto (9.2) y aplicar la migración (9.1).
2. Desplegar Orders (con modo NORMAL por defecto no cambia nada visible).
3. Desplegar la app del repartidor con `/deliver` (9.3).
4. Desplegar Delivery (cierra la puerta lateral).
5. **Recién ahí** habilitar el selector de modo SEGURO en la app del cliente (9.4).

**Por qué este orden:** el modo seguro solo es seguro si Delivery ya no deja marcar "Entregado" por su cuenta. Si se ofrece antes, un repartidor podría saltarse el código.

## 10. Pruebas

### 10.1 Qué se probó y cómo salió

| Qué | Resultado |
|---|---|
| Orders, pruebas automáticas (`./mvnw verify`) | **304 de 304** pasan, más **16 de 16** de integración (`*IT`) |
| Delivery, pruebas automáticas (`./gradlew :delivery-service:test`) | 210 pruebas, **5 fallan** y ya fallaban antes (ver 10.2) |
| **Prueba de punta a punta** con Auth, Catalog, Orders y Delivery reales y PostgreSQL reales | **Todo lo esperado pasó**: ver `INFORME_PRUEBA_PUNTA_A_PUNTA_ENTREGA_CON_CODIGO.md` (5 escenarios, incluidas una ráfaga de intentos simultáneos y la migración sobre datos existentes) |
| `openapi.yaml` de Orders | Se leyó correctamente y contiene el endpoint nuevo |

Pruebas nuevas que escribí (todas pasan):

| Prueba | Qué comprueba |
|---|---|
| `OrderDeliveryCodeTest` | Reglas: 3 intentos, renovación al tercer fallo, bloqueo tras el segundo código, modo normal nunca se bloquea |
| `HmacDeliveryCodeAdapterTest` | El código tiene 6 dígitos, nunca es `000000` en modo seguro, es estable, cambia al renovarse y entre pedidos, y falla si no hay clave |
| `VerifyDeliveryCodeUseCaseTest` (13 casos) | Entrega correcta, intentos, renovación, bloqueo, repartidor ajeno, cliente que se entrega a sí mismo, pedido no retirado, pedido ya entregado, pedido inexistente |
| `OrderControllerDeliverTest` | Que cada resultado se traduzca al código HTTP correcto (200/422/423/403/409/400) |
| `OrderControllerDetailIdFormatTest` | El detalle acepta el número, el UUID, y rechaza un id inválido |
| `GetOrderDetailUseCaseTest` (casos nuevos) | El código lo ve solo el cliente dueño; nunca el repartidor ni el restaurante; no se muestra tras entregar ni en modo normal |
| `CreateOrderUseCaseTest` (casos nuevos) | El modo se guarda, el código vuelve solo en modo seguro, sin modo se asume normal |
| `DeliveryCodeConcurrencyTest` | **PostgreSQL real**: 8 intentos erróneos simultáneos sobre un mismo pedido → 4 fallos simples, 1 renovación, 3 bloqueos y solo 6 intentos registrados. También valida la migración `V3` en una base real. |
| Delivery: `UpdateRouteStatusUseCaseImplTest`, `RouteOwnershipTest` | "Entregado" por la vía pública da 409 y no escribe nada |

Pruebas existentes que tuve que **modificar** (cambió el comportamiento a propósito):
- `UpdateOrderStatusUseCaseTest`: antes probaba que el repartidor pasara a "Entregado" con `PUT /status`; ahora prueba que eso se rechaza.
- `UpdateRouteStatusUseCaseImplTest` (TC1): antes actualizaba a "Entregado"; ahora usa "En camino".
- `InternalRoutesControllerTest`: ahora espera el estado normalizado ("Entregado").
- Varias pruebas de Orders solo se ajustaron para el constructor nuevo o para el id de texto del detalle (no cambia lo que prueban).

### 10.2 Las 5 pruebas de Delivery que fallan: por qué

**Ninguna se debe a este cambio.** Se ejecutaron sobre una copia limpia de `origin/main` (sin ninguno de nuestros cambios) y fallan exactamente las mismas 5.

| Prueba | Por qué falla |
|---|---|
| `DeliveryMigrationIT` (escenario 1) | Espera que exista el repartidor de prueba "U1", pero la migración `V6` lo borró. La prueba quedó desactualizada. |
| `JpaDeliveryPersonRepositoryIT` TC1 y TC4 | Lo mismo: buscan a "U1". |
| `JpaDeliveryPersonRepositoryIT` TC6 | Prueba la restricción de usuario único; la base devuelve el error de duplicado pero la prueba no lo espera así. **No se investigó la causa exacta** (probablemente también arrastra datos de `V6`). |
| `InternalApiKeyFilterRegistrationIT` TC1 | Espera que la clave interna **no** esté definida. Falla porque se define al ejecutar (`INTERNAL_API_KEY=local-test-key`), que es lo que piden las otras pruebas de integración. Sin esa variable, las pruebas con base de datos ni arrancan. |

Delivery **no tiene CI** en el repo (solo Auth, Catalog y Orders), así que estos fallos nadie los ve. Conviene arreglarlos en un cambio aparte (es trabajo de Delivery, ajeno a esta función).

### 10.3 Si las pruebas con base de datos fallan en tu máquina
- **Docker apagado:** las pruebas con base de datos real necesitan Docker encendido. Si no, fallan con "Could not find a valid Docker environment" (no es un error del código).
- **Docker 29:** ver la nota del workaround de Testcontainers (`~/.docker-java.properties`).

### 10.4 Pendientes de prueba (lo que todavía no está cubierto)

| Pendiente | Por qué importa | Quién |
|---|---|---|
| **Repetir y documentar el flujo** con la guía de la sección 4 del informe de punta a punta | Es la validación formal de la entrega | Nicolás |
| **Pasando por el gateway** | La prueba fue directa a los servicios. Debería funcionar (`/deliver` es POST), pero falta confirmarlo | Nicolás |
| **Delivery caído en el momento de entregar** | Orders dejaría el pedido "Entregado" y solo registraría un aviso; la ruta quedaría desactualizada (ver 11.2). No se probó | Nicolás / decisión del equipo |
| **RabbitMQ** | No estaba levantado; no se probaron los eventos | Quien tenga el ambiente |
| **Secreto real de FloCI** | Las pruebas usan claves de prueba | Quien despliegue |
| **Migración sobre la base real de FloCI** | Se ensayó sobre una copia local | Quien despliegue |
| **Apps móviles reales** | Todas las pruebas hicieron de app con `curl` | Equipo de la app |

### 10.5 Recomendación
Con lo hecho, no hacen falta más pruebas automáticas para esta función. Lo que aporta valor ahora es la **validación manual de Nicolás** (idealmente por el gateway y con las apps cuando estén adaptadas) y ensayar el despliegue en FloCI (9.1 y 9.2). Opcional: convertir los pasos del informe de punta a punta en una colección Postman, como el manual "Level 1" que ya existe.

## 11. Consideraciones para el resto del equipo

### 11.1 Formato del id del pedido (número vs UUID)
- La convención del proyecto es: **dentro de Orders se usa UUID; hacia afuera, el número** (`HANDOFF_ORDERS_FLASHDROP.md` §9.1).
- En la prueba de punta a punta `GET /api/orders/{id}` solo aceptaba el UUID mientras `PUT /status` y `/deliver` aceptaban también el número. **Lo corregí**: ahora los tres aceptan número o UUID.
- **Lo que no cambié:** las respuestas de crear pedido, listado y detalle siguen devolviendo el `id` como **UUID**, mientras que Delivery y "pedidos disponibles" entregan el número. Hoy todo funciona porque **cada rol usa el id que recibió** y todos los endpoints aceptan ambos formatos. Unificar las respuestas al número sería un cambio de contrato que afecta a la app; es una decisión aparte para el equipo de la app y Sebastián.

### 11.2 Si Delivery no responde al entregar
Orders marca el pedido "Entregado" y, si no puede avisar a Delivery, solo deja un aviso en el log: la ruta quedaría desactualizada. Es el comportamiento que ya tenía el cambio de estado (también pasa con "Retirado"). No se cambió ni se probó. Conviene decidir si se tolera o se agrega una reconciliación.

### 11.3 Otras consideraciones
- **Sin límite de solicitudes en el gateway.** Los overrides del gateway solo aceptan rutas exactas y `/api/orders/501/deliver` cambia con cada pedido. La protección real son los 3 intentos por código en Orders. Si se quiere esa segunda barrera, hay que ampliar el gateway para aceptar patrones.
- **Pedido bloqueado: no hay herramienta de soporte todavía.** Hoy el pedido queda bloqueado y habría que resolverlo directamente en la base de datos. Falta definir cómo lo destraba soporte.
- **Un bloqueo no avisa a nadie.** Queda en el registro (`WARN`) y en la tabla de auditoría, pero no se envía una alerta al cliente ni a soporte (no existe sistema de notificaciones).
- **Foto de entrega en modo normal:** sigue pendiente, para una segunda etapa. El compose de catalog usa `S3_ENDPOINT=http://floci:4566/`, así que FloCI parece ofrecer almacenamiento de archivos; no se verificó.
- **El Anexo A del informe anterior quedó desactualizado.** Decía que en Delivery faltaba validar el dueño de la ruta y devolver 404; en el `main` actual eso ya estaba hecho. Lo único que faltaba era rechazar "Entregado" y normalizar el estado interno (lo que se implementó). Conviene avisarle a Sebastián.
- **Perfil `supabase` de Orders:** las funciones nuevas no existen allí (lanzan "no soportado"). Es un perfil antiguo; el que se usa en FloCI es `postgres`.
- **Ruido anterior a este cambio:** Orders deja errores en el log al pedir `/api/internal/restaurants/{id}` al Catalog en memoria (no existe esa ruta; la respuesta sale bien); y la base de Auth del `main` no trae la semilla de roles, así que una instalación nueva no puede registrar usuarios hasta cargarla.

## 12. Resumen

**Desarrollo (nuestro): ✅ terminado.**
- [x] Orders y Delivery implementados, con pruebas automáticas y prueba de punta a punta.
- [x] Migración `V3` probada (base vacía y datos existentes).
- [x] Contrato (`openapi.yaml`), configuración y documentación actualizados.
- [x] Id del detalle del pedido alineado (número o UUID).

**Pendientes (otras personas o ambientes):**
- [ ] Nicolás: repetir y documentar el flujo (idealmente por el gateway) con `INFORME_PRUEBA_PUNTA_A_PUNTA_ENTREGA_CON_CODIGO.md` §4.
- [ ] Felipe: decidir el commit y el PR una vez que Nicolás valide (en pausa).
- [ ] Crear el secreto `flashdrop/delivery-code-secret` (9.2).
- [ ] Aplicar `V3__delivery_code.sql` a la base de Orders en FloCI y ensayarla allí (9.1).
- [ ] Equipo de la app: repartidor usa `/deliver` **antes** de desplegar Delivery (9.3); cliente con selector de modo y código (9.4).
- [ ] Decidir si se unifican los ids de las respuestas al número (11.1).
- [ ] Decidir qué hacer si Delivery no responde al entregar (11.2).
- [ ] Definir cómo destraba soporte un pedido bloqueado.
- [ ] Avisar a Sebastián que el Anexo A del informe anterior estaba desactualizado, y arreglar en un cambio aparte las 5 pruebas de Delivery que ya fallaban.
- [ ] (Segunda etapa) Foto de entrega en modo normal.

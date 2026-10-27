# Plan: Entrega protegida con código de verificación

**Para:** Sebastián (Delivery) · **De:** Felipe (Orders) · **Fecha:** 2026-10-22
**Servicios involucrados:** Orders (principal), Delivery, Gateway y app móvil.
**Lo que necesitamos de ti:** autorización para tocar Delivery y la rama a usar (ver sección 10 y Anexo A).

---

## 1. Qué problema resolvemos

Hoy el repartidor puede marcar un pedido como "Entregado" con solo apretar un botón. No hay nada que demuestre que el cliente realmente recibió el producto. Si el repartidor miente, el cliente no tiene cómo defenderse.

Además encontramos un segundo problema: el servicio de Delivery tiene una puerta lateral que también permite marcar "Entregado", y ni siquiera revisa que la ruta pertenezca al repartidor que la está tocando. Si no la cerramos, cualquier solución que hagamos en Orders se puede saltar.

## 2. La solución en una frase

Cada pedido tiene un **código de 6 números**. El cliente se lo dice al repartidor cuando recibe el producto, el repartidor lo escribe en su app, y **solo si coincide** el pedido pasa a "Entregado".

## 3. Cómo funciona, paso a paso

**Al hacer el pedido**, el cliente elige cómo quiere recibirlo:

- **Modo seguro**: se crea un código único y secreto para ese pedido. Si el cliente no tiene el código, el pedido **no se entrega**. Es responsabilidad del cliente tenerlo (por ejemplo, si se queda sin batería).
- **Modo normal**: el pedido se puede dejar en recepción o con otra persona. El código es siempre **000000** y la app del repartidor lo escribe automáticamente.

La app debe avisar claramente: **"El modo de recepción no se puede cambiar una vez realizado el pedido."**

**Una sola regla para todos los pedidos.** Para no crear dos flujos distintos, todos los pedidos tienen código y todos se entregan de la misma forma: ingresando el código. La única diferencia es que en modo normal el código es siempre 000000 y se escribe solo. Así el sistema tiene un único camino y no hay casos especiales.

**Durante la entrega:**

1. El repartidor retira el pedido (esto ya funciona hoy).
2. Llega donde el cliente. El cliente le dice su código.
3. El repartidor lo escribe en la app.
4. Si coincide: el pedido queda "Entregado" y el código **no se puede volver a usar**.
5. Si no coincide: se descuenta un intento.

**Quién ve el código:**

- El **cliente** lo puede ver siempre que quiera, en el detalle de su pedido, hasta que el pedido se entregue. Si se le olvida, lo vuelve a mirar.
- El **repartidor nunca lo ve**. Solo puede escribir el que le diga el cliente. Esto es lo que impide que él mismo se "entregue" el pedido.
- El restaurante tampoco lo ve.

## 4. Protección contra adivinar el código

El repartidor podría probar números hasta acertar, así que ponemos límites:

- Cada código permite **3 intentos**.
- Al fallar el tercero, ese código se **anula y se crea uno nuevo** (el cliente ve el nuevo en su app). Esto queda registrado como incidente.
- Esto puede pasar **como máximo 2 veces** (2 códigos, 6 intentos en total). Si también se agotan, el pedido queda **bloqueado** y solo soporte puede resolverlo.
- Los intentos los cuenta el servidor, no la app. Aunque el repartidor mande muchos intentos al mismo tiempo, el servidor no deja pasar más de 3 por código.
- En modo normal esto nunca se activa, porque el código siempre es 000000 y siempre acierta.
- Si el repartidor y el cliente fueran la misma persona (alguien que se pide a sí mismo), el sistema no le permite entregarse su propio pedido.

## 5. Qué hay que hacer en cada servicio

### Orders (donde está el trabajo principal)

- **Guardar el modo de recepción** y el estado del código en cada pedido: cuántos intentos van, cuántas veces se renovó y cuándo se entregó. Los pedidos antiguos quedan como modo normal, así no se rompe nada de lo existente. (Requiere una migración de base de datos nueva, `V3`, junto a las dos que ya existen en `db/migration`.)
- **Generar el código**: en modo seguro se calcula a partir del número del pedido y una clave secreta del sistema, por lo que el código no queda guardado en la base de datos. Nunca se genera 000000 por azar en modo seguro. La clave secreta se guarda en el Secrets Manager de FloCI, igual que la clave interna que ya existe.
- **Mostrar el código al cliente**: en el detalle del pedido (`OrderDetailResponse`) y al crear el pedido. Solo aparece si quien consulta es el cliente dueño y el pedido aún no se entregó. No aparece en listados, en mensajes entre servicios ni en logs.
- **Nueva acción "entregar con código"** para el repartidor: recibe el código, lo compara, cuenta los intentos y, si acierta, marca el pedido "Entregado". Reutiliza lo que ya hace hoy el cambio de estado (avisar a Delivery y publicar el evento).
- **Cerrar la forma antigua**: el cambio de estado directo a "Entregado" (`PUT /api/orders/{id}/status`) deja de funcionar. La única forma de entregar es con el código.
- **Avisar a la app del repartidor** si el pedido es seguro o normal, para que sepa si debe pedir el código o escribir 000000.
- **Documentación**: actualizar `openapi.yaml` de Orders (de paso corregimos la parte del "claim" que quedó desactualizada).

### Delivery (requiere tu autorización)

- **Verificar que la ruta es del repartidor** que intenta cambiarla (hoy cualquiera puede cambiar cualquiera).
- **Prohibir marcar "Entregado" por esta vía**. Delivery solo se entera de la entrega cuando Orders se lo avisa.
- **Arreglar el error** que hoy aparece como falla del servidor cuando la ruta no existe; debe responder "no encontrada".
- **Unificar cómo se escribe el estado** ("Entregado" vs "ENTREGADO" hoy conviven en la base de datos).
- Delivery no sabe nada del código; no lo necesita. Tampoco requiere migración de base de datos.

El detalle con archivos y líneas está en el **Anexo A**.

### Gateway

- Agregar un límite de solicitudes por minuto a la acción "entregar con código". Es una segunda barrera; la principal es el conteo de intentos en Orders.

### App móvil (Flutter, fuera de este repositorio)

- Pantalla de compra con selector de modo + aviso de que no se puede cambiar.
- Cliente: ver su código en el detalle del pedido.
- Repartidor: campo para escribir el código (en modo normal se completa solo con 000000).
- Definiremos el contrato (qué se envía y qué se recibe) para que lo implementen.

## 6. Foto de entrega en modo normal (segunda etapa)

En modo normal, el repartidor podrá **tomar una foto del pedido entregado** como respaldo si luego el cliente reclama. Es opcional. Antes de construirla hay que decidir **dónde se guardan las fotos**: hoy el proyecto no tiene almacenamiento de archivos y hay que revisar si FloCI ofrece uno (tipo S3). Por eso va en una etapa separada y no frena la parte principal.

## 7. Orden de trabajo

1. **Confirmar contigo**: que autorices tocar Delivery y qué rama usar (o si todo está en `main`). Comprobar el estado real de `main` antes de empezar.
2. **Orders**: modo de recepción, código, nueva acción de entrega, límites de intentos, pruebas, documentación.
3. **Delivery** (tras tu autorización): cerrar la puerta lateral y arreglos asociados.
4. **Gateway**: límite de solicitudes.
5. **Contrato para la app móvil**.
6. **Foto de entrega** (cuando se defina el almacenamiento).

Importante: el modo seguro solo debe ofrecerse a clientes cuando el paso 3 esté desplegado; si no, el repartidor aún podría saltarse el código por Delivery.

## 8. Cómo comprobamos que funciona

- **Pruebas automáticas en Orders**: código correcto entrega; código incorrecto descuenta intento; el tercer error crea código nuevo; tras 2 renovaciones el pedido se bloquea; un repartidor ajeno no puede entregar; el cliente ve el código y nadie más; en modo normal 000000 siempre entrega; el cambio de estado directo a "Entregado" queda rechazado.
- **Prueba de simultaneidad**: muchos intentos erróneos a la vez nunca superan los 3 permitidos.
- **Pruebas en Delivery**: un repartidor no puede tocar rutas ajenas y no puede marcar "Entregado" por su cuenta.
- **Prueba completa en el entorno local**: crear pedido seguro → repartidor lo toma y retira → 3 códigos malos (el código se renueva y el cliente ve el nuevo) → código bueno → "Entregado" y la ruta en Delivery también queda entregada. Luego intentar marcar "Entregado" directamente en Delivery y confirmar que lo rechaza.

## 9. Decisiones ya tomadas

| Tema | Decisión |
|---|---|
| Intentos | 3 por código |
| Renovaciones | máximo 2 códigos; luego bloqueo y soporte |
| Visibilidad | el cliente siempre ve su código; el repartidor nunca |
| Si la app no envía el modo | se asume modo normal (código 000000) |
| Cambio de modo | no se puede cambiar; la app lo avisa |
| Delivery | lo implementamos nosotros, con tu autorización |
| Foto | opcional en modo normal, segunda etapa |

## 10. Puntos por confirmar

- "Tope de 2 ciclos" se interpretó como **2 códigos en total (6 intentos)**. Si se quería decir 2 renovaciones adicionales (3 códigos, 9 intentos), se ajusta.
- Dónde se almacenarán las fotos de entrega.
- Rama a usar para Delivery una vez que respondas.

---

## Anexo A — Detalle para Delivery

Rutas relativas a `services/delivery-service/src/main/java/com/flashdrop/delivery`.

| # | Qué pasa hoy | Qué pedimos | Dónde |
|---|---|---|---|
| 1 | `PUT /routes/{routeId}/status` no revisa que la ruta sea del repartidor que la modifica; cualquier repartidor puede cambiar cualquier ruta | Pasar el usuario del token al caso de uso y rechazar (403) si la ruta no es suya | `RouteController` (l.62-69), `UpdateRouteStatusUseCaseImpl` (l.34-46) |
| 2 | Esa misma ruta permite marcar `ENTREGADO` | Rechazar `ENTREGADO` por esta vía (409). Solo Orders lo marca, mediante el aviso interno ya existente `PATCH /api/internal/routes/order/{orderId}/status` | mismo caso de uso |
| 3 | Si la ruta no existe, el sistema falla con error 500 | Responder 404 "ruta no encontrada" (la excepción `RouteNotFoundException` ya existe) | `JpaRouteRepositoryAdapter` (l.66-76), `UpdateRouteStatusUseCaseImpl` (l.43) |
| 4 | La base mezcla "Entregado" y "ENTREGADO" | Normalizar siempre con `RouteStatus` antes de guardar | `InternalRoutesController` (l.68 y l.89) |
| 5 | Un acceso denegado responde 401 en vez de 403 | Corregir el código (menor, opcional en este alcance) | `GlobalExceptionHandler` (l.136-139) |

**Pruebas esperadas:** repartidor ajeno → 403; `ENTREGADO` por la vía pública → 409; ruta inexistente → 404; el aviso interno de Orders sigue pudiendo marcar `ENTREGADO`.

**Preguntas para ti:**

1. ¿Autorizas que nosotros implementemos estos cambios en Delivery?
2. ¿Sobre qué rama trabajamos, o todo lo vigente está en `main`?
3. ¿La app del repartidor usa hoy `PUT /routes/{id}/status` para marcar "Entregado"? Si sí, hay que coordinar el cambio con quien hace la app.
4. ¿Hay algún otro cliente (script, panel) que dependa de poder marcar `ENTREGADO` por esa vía?

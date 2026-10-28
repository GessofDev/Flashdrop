# Entrega con código: respuesta a la revisión

**Para:** Nicolás · **De:** Felipe · **Fecha:** 2026-10-27
**Rama:** `feat/delivery-code-verification` (commit `b5ca8ef`, encima de `ec30983`)

## 1. Resumen

Apliqué los **3 cambios obligatorios** y los **recomendados** de tu revisión. La rama está lista para abrir el PR, con dos puntos abiertos que están en la sección 5: el `Co-Authored-By` del primer commit y la prueba por el gateway.

| Resultado | Detalle |
|---|---|
| Orders | Todas las pruebas pasan, unitarias y de integración |
| Delivery | 208 de 213 pasan (con Docker y `INTERNAL_API_KEY`). Las 5 que fallan **ya fallaban en `main`**, igual que antes |
| Prueba de punta a punta | **No se repitió** con servicios levantados después de estos cambios; solo hay pruebas automáticas |

## 2. Cambios obligatorios

| # | Qué pediste | Qué hice | Dónde |
|---|---|---|---|
| 1 | Bloquear "Entregado" por `PUT /status` solo en pedidos SEGURO | En SEGURO responde **409**. En NORMAL el `PUT` entrega, actualiza el pedido y sincroniza la ruta en Delivery como antes. La app actual no se rompe al desplegar | `UpdateOrderStatusUseCase` |
| 2 | Que las rutas de Delivery traigan el modo | Orders envía `receptionMode` al crear la ruta. Delivery lo acepta (opcional, `NORMAL` por defecto), lo guarda y lo devuelve junto a `deliveryPinRequired` (verdadero solo en SEGURO) en `GET /api/delivery/routes` | `CreateOrderUseCase`, `DeliveryRoute`, `DeliveryHttpClientAdapter`, `CreateDeliveryRouteRequest`, `RouteResponse` |
| 2 | Migración en Delivery | `V7__delivery-routes-reception-mode.sql`: columna `reception_mode` con `NORMAL` por defecto y restricción `NORMAL`/`SEGURO`. Las rutas existentes quedan en NORMAL | `services/delivery-service/.../db/migration` |
| 3 | Si falta la clave, fallar antes de guardar y con 4xx | Nuevo `isSecureModeAvailable()` en el puerto del código. Al inicio de crear pedido, si es SEGURO y no hay clave, responde **400** sin guardar nada, sin crear ruta y sin publicar evento. El código se calcula justo después de guardar, antes de crear la ruta | `DeliveryCodePort`, `HmacDeliveryCodeAdapter`, `CreateOrderUseCase` |

Lo que no cambia: la ruta de Delivery sigue rechazando "Entregado" por su `PUT` público (409). Solo Orders puede marcarla, por el aviso interno.

## 3. Recomendados y menores

| Punto | Estado |
|---|---|
| Intentos restantes dentro del `message` del 422 | ✅ "Codigo incorrecto. Quedan 2 intentos." (o "Queda 1 intento."). Si se renueva el código, el mensaje lo dice y también los intentos del código nuevo. Siguen los campos `remainingAttempts` y `codeRenewed` |
| Clave en `gateway/docker/.env`, no en Secrets Manager | ✅ Corregido en `INFRASTRUCTURE.md` y en la sección 9.2 del informe de implementación. Quité la referencia al secreto de la task definition de Orders |
| `V3` re-ejecutable | ✅ `DROP CONSTRAINT IF EXISTS chk_orders_reception_mode` antes del `ADD CONSTRAINT` |
| Mover los 3 informes a `docs/plans/` | ✅ Hecho |
| Quitar el `Co-Authored-By` del commit | ⚠️ **Parcial.** El commit nuevo no lo lleva, pero `ec30983` sí. Ver sección 5 |

También actualicé el `openapi.yaml` de Orders (el `PUT /status` ahora describe que el 409 es solo para SEGURO) y el informe de implementación y el de la prueba de punta a punta, para que no digan lo contrario.

## 4. Pruebas

### 4.1 Pruebas nuevas o cambiadas

| Prueba | Qué comprueba |
|---|---|
| `UpdateOrderStatusUseCaseTest` | Pedido SEGURO: "Entregado" por `PUT` lanza 409 y no escribe nada. Pedido NORMAL: entrega y sincroniza la ruta ("Entregado") |
| `CreateOrderUseCaseTest` | SEGURO sin clave: error 400 y **cero interacciones** con repositorio, Delivery y eventos. SEGURO con clave: devuelve el código |
| `OrderControllerDeliverTest` | El `message` del 422 incluye los intentos restantes |
| `InternalRoutesControllerTest` (Delivery) | Ruta SEGURO devuelve `deliveryPinRequired=true`; sin modo queda NORMAL y `false`; un modo inválido da 400 |

### 4.2 Resultado de las suites

| Suite | Resultado |
|---|---|
| Orders (`./mvnw verify`) | ✅ verde |
| Delivery (`./gradlew :delivery-service:test`, desde `services/`) | 208 de 213. Fallan las mismas 5 de siempre: 3 por la semilla "U1" que borra `V6`, 1 por constraint única y 1 que espera que falte `INTERNAL_API_KEY` |

La `V7` se ejecuta contra un Postgres real (Testcontainers) en las pruebas de integración de rutas, y pasan.

## 5. Pendientes

| Pendiente | Por qué importa | Quién |
|---|---|---|
| **`Co-Authored-By` en `ec30983`** | `AGENTS.md` lo prohíbe. Quitarlo exige reescribir el historial (reset + force-push), y la herramienta que uso lo bloqueó. Opciones: que lo hagas tú, que me autorices a hacerlo, o unir el PR con *squash* y mensaje propio para que no llegue a `main` | Tú / Felipe |
| **Abrir el PR** | `gh` no está instalado en mi máquina. Enlace: https://github.com/GessofDev/Flashdrop/pull/new/feat/delivery-code-verification | Felipe |
| **Repetir el flujo vía gateway** | Los cambios de esta ronda se verificaron solo con pruebas automáticas. Falta: NORMAL por `PUT`, y SEGURO con intentos fallidos, renovación, bloqueo y entrega; confirmar que `GET /api/delivery/routes` trae `deliveryPinRequired`; y `GET /api/orders/{número}` | Nicolás |
| **Aplicar `V3` a mano en FloCI** | Orders tiene `FLYWAY_ENABLED=false`. Sin la migración, Orders no puede leer pedidos | Nicolás (despliegue) |
| **Definir `DELIVERY_CODE_SECRET` en `gateway/docker/.env`** | Sin la clave el modo SEGURO responde 400; NORMAL funciona igual | Nicolás (despliegue) |
| **Desplegar Orders y Delivery juntos** | La `V7` se aplica sola. Si Orders nuevo se despliega con un Delivery viejo, la ruta ignora `receptionMode` y todo queda NORMAL; y un Delivery nuevo con un Orders viejo deja todo NORMAL. No rompe, pero SEGURO no funcionaría | Nicolás (despliegue) |
| **Adaptar las apps** | Javier: la app del repartidor debe pedir el código según `deliveryPinRequired` y usar `POST /deliver` en SEGURO; la del cliente, el selector de modo y mostrar `deliveryCode`. **No activar el selector SEGURO hasta que ambas estén listas** | Javier |
| **Texto del PR** | El texto que preparé decía que `PUT /status` a Entregado da 409 para todos y que el secreto va en Secrets Manager. Ya lo corregí | Felipe |

## 6. Consideraciones para el equipo

1. **NORMAL no es seguro por diseño.** Cualquiera que conozca `000000` puede entregar un pedido NORMAL por `PUT`. Es la decisión acordada para no romper la app; la protección real es el modo SEGURO.
2. **Hasta que Javier active el selector, todos los pedidos son NORMAL.** Desplegar no cambia nada visible para la app actual.
3. **Pedido bloqueado (423):** todavía no hay herramienta de soporte para destrabarlo.
4. **Delivery caído al entregar:** Orders deja el pedido "Entregado" igual y solo registra un aviso; la ruta quedaría desactualizada. Es el comportamiento que ya existía.
5. **Delivery no tiene CI** y arrastra 5 pruebas rotas ajenas a este cambio. Conviene arreglarlas aparte.
6. **No rotar `DELIVERY_CODE_SECRET`** con pedidos SEGURO en curso: cambiaría los códigos que los clientes ya tienen.
7. **Foto de entrega en NORMAL:** sigue como segunda etapa.

## 7. Orden de despliegue

1. Se abre el PR y se une a `main`.
2. Aplicar `V3` a mano en la base de Orders y poner `DELIVERY_CODE_SECRET` en el `.env`.
3. Desplegar Orders y Delivery juntos (la `V7` se aplica sola).
4. Probar por el gateway (ver sección 5).
5. Javier adapta la app y después activa el modo SEGURO.

## 8. Cierre

El desarrollo que pediste está hecho y probado con pruebas automáticas. Lo que queda es la prueba por el gateway, el despliegue y la decisión sobre el `Co-Authored-By`. Detalle completo en `INFORME_IMPLEMENTACION_ENTREGA_CON_CODIGO.md` y `INFORME_PRUEBA_PUNTA_A_PUNTA_ENTREGA_CON_CODIGO.md` (en esta misma carpeta).

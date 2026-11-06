# Feature: eliminar la cuenta (F5)

> Origen: `FlashDrop-1.0-Analisis-Brechas.docx` (F5) y `FlashDrop-1.0-Plan-Consolidado.docx`
> (semana 3, caso de prueba F23). Google Play lo exige a toda app que permita crear cuentas y la
> Ley 21.719 (vigente desde 01-12-2026) da derecho a pedir la eliminacion de los datos.
> Alcance: **solo backend**. La opcion en la app y la vista web las hacen Javier y Sebastian sobre
> `DELETE /auth/account`.

## Decisiones tomadas (Felipe, 2026-11-06)

1. **Procesos en curso: no se cancela nada automaticamente.** Si la persona tiene pedidos en curso
   (como cliente, dueno de tienda o repartidor asignado) la eliminacion se **rechaza con 409** y debe
   cancelarlos o cerrarlos por el flujo normal antes. "En curso" = ni `Entregado`, ni `Cancelado`, ni
   `Rechazado`. (Reemplaza la idea inicial de cancelarlos al eliminar.)
2. **Pedidos con disputa o devolucion:** hoy no existe el concepto. Los pedidos terminados se conservan
   como evidencia minima (id, estado, montos, fechas, tienda y productos) sin ningun dato del cliente.
   Si mas adelante existe un id de disputa, se conserva junto a esos datos minimos.
3. **Listas tras eliminar:** sin texto sustituto; el nombre del cliente y la direccion quedan vacios.
4. **Dueno de tienda:** se borra el vinculo con el dueno y la tienda queda **cerrada**; el restaurante y su
   catalogo se conservan para asignarlo a otro dueno.
5. **Verificacion de identidad:** quien tiene la contrasena de la cuenta puede eliminarla (la vista web
   usa el mismo endpoint). Cualquier otro metodo queda como pendiente (ver abajo).
6. **Alcance de datos:** se borran todos los datos personales en los cuatro servicios, no solo en Orders.

## Pendiente de decision (no se implementa porque no esta en el documento del plan)

- **Evasion** (alguien sancionado elimina su cuenta y se registra otra vez): conservar un registro minimo
  exige definicion legal.
- **Solicitud web sin poder iniciar sesion:** el plan pide contemplar una via web. Para quien no puede
  entrar, el metodo de verificacion propuesto es un correo de confirmacion (el mismo mecanismo de recuperar
  contrasena, que ya existe: `PasswordResetTokenStore` y `PasswordResetMailer`). Sin implementar.
- **Plazos y excepciones de conservacion** (contabilidad, SII): los define la asesoria legal.
- **Logs y auditoria:** `AuditLogger` registra el id del usuario y la IP del cliente; revisar con legal.
- Observacion: `internal.delivery` (tabla legada de Delivery con direcciones) no la usa ningun codigo, por
  eso no se anonimiza.

## Flujo

```
DELETE /auth/account {password}  (Bearer)
  1. Auth valida el token y la contrasena (con limite de intentos).
  2. Auth pregunta a Orders por pedidos en curso (GET /api/internal/users/{id}/deletion-blockers).
     - Hay pedidos -> 409 ACCOUNT_HAS_ACTIVE_PROCESSES (nada cambia, la cuenta sigue funcionando).
     - Orders no responde -> 503 (nunca se asume "sin pedidos").
  3. Se registra la solicitud (account_deletions, V5), la cuenta queda inhabilitada (login.status=0) y se
     revocan sus sesiones. -> 202. Los tokens ya emitidos dejan de valer al instante: Orders valida cada
     request contra /auth/validate, que ahora rechaza cuentas inhabilitadas.
  4. En segundo plano (cada 30 s, reintentos 1,2,4... min con tope de 1 h, 20 intentos):
     a. Orders   POST /api/internal/users/{id}/erase
     b. Delivery POST /api/internal/erasure/delivery-persons?userId={id}
     c. Catalog  POST /api/internal/erasure/restaurant-owner?userId={id}
     d. Auth borra al usuario (cascada: login, roles, sesiones, tokens de recuperacion) y la solicitud.
```

- Cada paso es idempotente y su avance se guarda: un reintento continua donde quedo.
- Si en (a) Orders encuentra pedidos nuevos en curso (carrera), la eliminacion se **aborta y la cuenta se
  reactiva**. Tras 20 intentos fallidos la solicitud queda `FAILED` y se registra un error en logs para
  intervencion manual (la cuenta sigue inhabilitada).
- Orders: borra la direccion de todos los pedidos del cliente, pide a Delivery borrar la direccion de sus
  rutas, anonimiza el actor y el motivo (texto libre) de las operaciones de entrega y, **al final**, desvincula
  el perfil de cliente (`client.user_id = NULL`, `end_date`; V5 hace la columna nullable). El vinculo se borra
  al final para que un reintento tras un fallo parcial siga encontrando los pedidos.
- Delivery: borra la direccion de entrega de las rutas y el perfil de repartidor (desvinculando antes sus rutas).
- Catalog: `restaurant.user_id = NULL` (V6) y la tienda queda cerrada.

## Migraciones

Auth **V5** (`account_deletions`), Orders **V5** (`client.user_id` nullable), Catalog **V6** (`restaurant.user_id`
nullable, del PR de F6). Delivery sin migracion.

## Configuracion

Auth: `ORDERS_SERVICE_URL`, `DELIVERY_SERVICE_URL`, `CATALOG_SERVICE_URL` (por nombre de servicio en la red de FloCI;
agregadas a `gateway/docker/docker-compose.stack.yml` y a `infra/floci/task-definitions/auth-service.dev.json`) y
`ACCOUNT_DELETION_ENABLED|POLL_MS|MAX_ATTEMPTS`. Usa `INTERNAL_API_KEY` (el mismo secreto compartido).

## Pruebas

- Unitarias: `RequestAccountDeletionServiceTest`, `ProcessAccountDeletionServiceTest`,
  `ActiveAccountTokenValidatorTest` (Auth); `GetAccountDeletionBlockersUseCaseTest`,
  `EraseUserDataUseCaseTest` (Orders); `EraseDeliveryDataUseCaseTest`; `EraseRestaurantOwnerUseCaseTest`.
- Slices: `AccountControllerTest`, `InternalUserDataControllerTest`, `InternalErasureControllerTest` (Delivery).
- Integracion con PostgreSQL: `AuthAccountDeletionIT` (flujo completo con Orders, Delivery y Catalog simulados:
  rechazo, aceptacion, reintento, aborto, cascada), `JpaOrderRepositoryAdapterTest`, `JpaClientAdapterTest`,
  `JpaRouteRepositoryIT`, `JpaRestaurantRepositoryAdapterIT`, `InternalErasureControllerIT` (Catalog).

## Caso F23 (punta a punta por API, con una cuenta creada por la prueba; nunca las cuentas demo)

1. Registrar un usuario nuevo U; login; crear un pedido en curso.
2. `DELETE /auth/account` con la contrasena correcta -> **409** y la cuenta sigue funcionando.
3. Llevar el pedido a `Entregado` (o cancelarlo) por el flujo normal.
4. `DELETE /auth/account` con contrasena incorrecta -> 401. Con la correcta -> **202**.
5. Login de U -> 401; el token anterior en `GET /auth/validate` y en `GET /api/orders` -> 401; refresh -> 401.
6. Esperar al ciclo de fondo (<= 30 s). `GET /auth/profile` falla; `users`, `login`, `user_has_roles` y
   `refresh_tokens` ya no tienen a U.
7. En Orders el pedido sigue con su estado y montos, con `address = ''` y sin cliente; la tienda lo ve sin
   nombre ni direccion del cliente; en Delivery su ruta tiene `delivery_address = ''`.
8. Variante repartidor y tienda: con un pedido asignado/pendiente la eliminacion se rechaza (409); al terminar
   se acepta, el perfil de repartidor desaparece y la tienda queda cerrada y sin dueno.
9. Resiliencia: apagar Orders antes del paso 4 -> `DELETE` responde 503; apagarlo despues del 202 -> el borrado
   se completa solo cuando vuelve.

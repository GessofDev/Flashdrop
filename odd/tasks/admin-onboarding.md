# Feature: alta de tiendas y repartidores (F6)

> Origen: `FlashDrop-1.0-Analisis-Brechas.docx` (F6) y `FlashDrop-1.0-Plan-Consolidado.docx`
> (semana 4, caso de prueba F24). Alcance de esta rama: **solo backend**; la pantalla de
> administracion de la web queda para el equipo web sobre los endpoints de abajo.

## Decision de diseno: el administrador

No existe un rol "Admin". El administrador es el **usuario con los tres roles `Cliente`,
`Restaurante` y `Repartidor` a la vez** (como `admin@demo.cl`, id 4, del seed de desarrollo en
`services/auth-service/src/main/resources/db/seed`). Por eso no hay migracion de roles ni cambios
en los enums de roles de Orders y Delivery (que rechazan roles desconocidos).

- Auth: `AdminAccess.isAdmin(roles)`; el controlador valida el token y el servicio exige el rol.
- Catalog y Delivery: matcher `allOf(hasRole(Cliente), hasRole(Restaurante), hasRole(Repartidor))`
  en `SecurityConfig`.
- En produccion el seed de desarrollo no se carga: el primer administrador se crea asignando los
  tres roles con `PUT /api/internal/users/{id}/roles` (clave interna) desde el servidor.
- Un administrador no puede cambiar sus propios roles (evita bloquear la administracion).

## Endpoints nuevos (todos por el gateway, JWT obligatorio)

| Servicio | Endpoint | Resultado |
|---|---|---|
| Auth | `GET /auth/admin/users?email=` | 200 `{id,name,lastName,email,roles}`; 401/403/404 |
| Auth | `PUT /auth/admin/users/{id}/roles` `{roles}` | 200; Cliente, Restaurante, Repartidor y Soporte; 400/401/403/404 |
| Catalog | `POST /api/catalog/admin/restaurants` `{name,address?,ownerUserId?}` | 201, nace **cerrado**; 400/403/409 |
| Catalog | `PUT /api/catalog/admin/restaurants/{id}/owner` `{userId}` | 200; 403/404/409 |
| Catalog | `GET /api/catalog/admin/restaurants` | 200 lista con dueno |
| Delivery | `POST /api/delivery/admin/persons` `{userId,vehicle?}` | 201; 400/403/409 (ya existe) |

Migracion: Catalog **V6** (`restaurant.user_id` pasa a nullable; el `UNIQUE` se mantiene).
Cambio de contrato: asignar un dueno que ya tiene otro local responde **409** (antes 400) tambien
en `PUT /api/internal/restaurants/{id}/owner`.

## Flujo de alta (lo que debe llamar la pantalla web)

Tienda: la persona se registra (queda Cliente) -> `GET /auth/admin/users?email=` ->
`PUT /auth/admin/users/{id}/roles` con `["Cliente","Restaurante"]` ->
`POST /api/catalog/admin/restaurants` con `ownerUserId` -> el dueno abre la tienda y define su
horario en `/api/catalog/my/restaurant/*`.

Repartidor: la persona se registra -> buscar por correo -> asignar `["Cliente","Repartidor"]` ->
`POST /api/delivery/admin/persons`.

## Pruebas

- Unitarias: `CreateRestaurantUseCaseTest`, `AdminUserServiceTest`.
- Slices/seguridad: `AdminUserControllerTest`, `AdminDeliveryPersonControllerTest`,
  `AdminRestaurantControllerIT` (perfil `local`).
- Integracion con PostgreSQL: `JpaRestaurantRepositoryAdapterIT` (V6), `AuthRoleAssignmentIT`
  (administrador asigna roles por correo; no administradores 403).

## Caso F24 (punta a punta por API, con cuentas creadas por la prueba)

1. Registrar dos usuarios nuevos A (dueno) y B (repartidor). Login con el administrador.
2. Admin: buscar A y B por correo y asignarles Restaurante y Repartidor.
3. Admin: crear la tienda con `ownerUserId = A`; verificar 201 y `acceptingOrders=false`.
4. A (nuevo login): `GET /api/catalog/my/restaurant` -> 200; abrir la tienda; aparece en
   `GET /catalog/restaurants` con `open=true`.
5. Un cliente hace un pedido a esa tienda; A lo ve en `GET /api/orders`.
6. Admin: `POST /api/delivery/admin/persons` para B; B ve el pedido en disponibles cuando A lo
   deja `Listo para retiro`.
7. Negativos: un usuario sin los tres roles recibe 403 en cada endpoint admin; un segundo local
   para A responde 409.

## Fuera de alcance

Pantalla de administracion (web), F18 (desbloqueo de pedidos), alta de usuarios por el
administrador (la persona se registra sola) y validar contra Auth que el dueno tenga el rol
Restaurante al crear el local (un dueno sin ese rol recibe 403 en `/api/catalog/my/**`).

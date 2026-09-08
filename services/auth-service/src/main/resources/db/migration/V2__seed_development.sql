-- =====================================================================
-- V2 - Datos de desarrollo de auth-service
--
-- Repetible y seguro: cada insert usa `on conflict do nothing`.
--
-- Los IDs son fijos a proposito y estan coordinados con los seeds de
-- Catalog, Orders y Delivery. Al separar las bases ya no hay claves
-- foraneas entre servicios que lo garanticen: si estos IDs cambian, el
-- flujo end-to-end falla con errores de "usuario no encontrado" que no
-- apuntan a la causa.
-- =====================================================================

-- =====================================================================
-- auth-service — datos demo
-- MIGRATION_PLAN.pdf §4.1: "Crear script de seed con datos demo para la
-- BD independiente de Auth."
--
-- ⚠️ IDs FIJOS A PROPÓSITO — NO CAMBIAR
--
-- Al separar las bases desaparecen las FK entre servicios: client.user_id
-- (Orders), restaurant.user_id (Catalog) y delivery.user_id (Delivery)
-- apuntan a estos usuarios y Postgres ya no puede validarlo. Si estos IDs
-- cambian, los seeds de los otros tres servicios quedan colgando y el E2E
-- de la Fase 4 falla con "usuario no encontrado" sin explicación obvia.
--
-- Contrato de IDs compartido con el equipo:
--   1 → cliente@demo.cl        (Felipe:    client.user_id = 1)
--   2 → restaurante@demo.cl    (Javier:    restaurant.user_id = 2)
--   3 → repartidor@demo.cl     (Sebastián: delivery.user_id = 3)
--   4 → admin@demo.cl          (multirol: cliente + restaurante + repartidor)
--   5 → araucomaipu@flashdrop.cl (Javier: restaurant.user_id = 5)
--
-- Aplicar DESPUÉS de 01_schema.sql:

-- =====================================================================

-- ---------------------------------------------------------------------
-- roles
-- `route` es la pantalla inicial de cada rol en la app Flutter.
-- ---------------------------------------------------------------------
insert into public.roles (id, name, image, route) values
  (1, 'Cliente',    null, '/client/products/list'),
  (2, 'Restaurante', null, '/restaurant/orders/list'),
  (3, 'Repartidor',  null, '/delivery/orders/list')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
insert into public.users (id, email, rut, name, last_name, phone, photo) values
  (1, 'cliente@demo.cl',          '11.111.111-1', 'Cliente',     'Demo',          '+56911111111', null),
  (2, 'restaurante@demo.cl',      '22.222.222-2', 'Restaurante', 'Demo',          '+56922222222', null),
  (3, 'repartidor@demo.cl',       '33.333.333-3', 'Repartidor',  'Demo',          '+56933333333', null),
  (4, 'admin@demo.cl',            '44.444.444-4', 'Admin',       'Multirol',      '+56944444444', null),
  (5, 'araucomaipu@flashdrop.cl', '55.555.555-5', 'Flash Bites', 'Arauco Maipu',  '+56955555555', null)
on conflict (id) do nothing;

-- ---------------------------------------------------------------------
-- login
--
-- El hash es el mismo del seed del monolito (bcrypt cost 10, prefijo $2b$),
-- para que las credenciales demo que ya usa el equipo sigan funcionando.
-- BCryptPasswordEncoder de Spring Security acepta $2a$/$2b$/$2y$, así que
-- el login contra este hash funciona sin volver a registrar a nadie.
--
-- Nota: PasswordPolicy (mín. 10 caracteres, mayúscula + minúscula + dígito)
-- se aplica SOLO en el registro, no en el login. Estas cuentas demo son
-- anteriores a la política y por eso siguen entrando.
-- ---------------------------------------------------------------------
insert into public.login (id, login, password, id_users, status) values
  (1, 'cliente@demo.cl',          '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 1, 1),
  (2, 'restaurante@demo.cl',      '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 2, 1),
  (3, 'repartidor@demo.cl',       '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 3, 1),
  (4, 'admin@demo.cl',            '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 4, 1),
  (5, 'araucomaipu@flashdrop.cl', '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 5, 1)
on conflict (id) do nothing;

-- ---------------------------------------------------------------------
-- user_has_roles
-- El usuario 4 (admin) tiene los tres roles: sirve para probar que
-- GET /api/internal/users/4/roles devuelve una lista de 3 elementos.
-- ---------------------------------------------------------------------
insert into public.user_has_roles (id, id_user, id_rol) values
  (1, 1, 1),
  (2, 2, 2),
  (3, 3, 3),
  (4, 4, 1),
  (5, 4, 2),
  (6, 4, 3),
  (7, 5, 2)
on conflict (id) do nothing;

-- El usuario 5 queda a propósito con un solo rol y sin roles extra;
-- para probar el caso "lista vacía" del contrato, usar un id inexistente
-- o crear un usuario nuevo por /auth/register (nace solo con 'Cliente').

-- ---------------------------------------------------------------------
-- Sincronizar las secuencias de identidad.
--
-- IMPRESCINDIBLE: las tablas son `generated by default as identity`, así
-- que los INSERT con id explícito de arriba NO avanzan la secuencia. Sin
-- este bloque, el primer POST /auth/register intenta insertar id = 1 y
-- revienta con 23505 (duplicate key).
-- ---------------------------------------------------------------------
select setval(pg_get_serial_sequence('public.users', 'id'),
              coalesce((select max(id) from public.users), 1));
select setval(pg_get_serial_sequence('public.login', 'id'),
              coalesce((select max(id) from public.login), 1));
select setval(pg_get_serial_sequence('public.roles', 'id'),
              coalesce((select max(id) from public.roles), 1));
select setval(pg_get_serial_sequence('public.user_has_roles', 'id'),
              coalesce((select max(id) from public.user_has_roles), 1));
select setval(pg_get_serial_sequence('public.refresh_tokens', 'id'),
              coalesce((select max(id) from public.refresh_tokens), 1));

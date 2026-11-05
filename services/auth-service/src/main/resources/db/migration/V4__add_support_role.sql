-- =====================================================================
-- V4 - Rol canónico de soporte
--
-- `Soporte` autoriza la intervención de pedidos ya retirados y la lectura
-- de su bitácora de entrega. Se agrega como dato de catálogo para que el
-- flujo existente de asignación de roles lo resuelva sin código extra.
--
-- La ruta es metadata de navegación para un consumidor futuro. Esta feature
-- es API/backend y no incluye una pantalla de soporte.
-- =====================================================================

insert into public.roles (name, image, route)
values ('Soporte', null, '/support/orders')
on conflict (name) do nothing;

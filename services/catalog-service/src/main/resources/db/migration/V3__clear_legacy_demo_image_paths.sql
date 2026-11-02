-- Los datos de demo guardan rutas de imagen del frontend viejo
-- (`assets/img/*.png`). Esos archivos no existen en este despliegue: el
-- navegador los pide y recibe 404.
--
-- Las imagenes reales viven en el bucket privado como
-- `products/<year>/<month>/<uuid>.<ext>` y se sirven por `/catalog/images/**`.
-- Se limpian las rutas heredadas para que la app muestre el placeholder en
-- lugar de pedir archivos que no existen.
--
-- Solo se tocan las tablas cuya columna `image` mapea una entidad (`products`
-- y `categories`). La tabla `restaurant` tiene la columna en la migracion
-- V1, pero ninguna entidad la mapea, asi que el esquema que arman las
-- pruebas de integracion no la tiene y la sentencia fallaria con
-- "column image does not exist".
--
-- Es idempotente: volver a aplicarla no cambia nada.

update public.categories set image = null where image like 'assets/%';
update public.products set image = null where image like 'assets/%';

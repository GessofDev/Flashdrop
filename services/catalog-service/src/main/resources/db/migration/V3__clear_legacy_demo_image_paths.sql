-- Los datos de demo guardan rutas de imagen del frontend viejo
-- (`assets/img/*.png`). Esos archivos no existen en este despliegue: el
-- navegador los pide y recibe 404.
--
-- Las imagenes reales viven en el bucket privado como
-- `products/<year>/<month>/<uuid>.<ext>` y se sirven por `/catalog/images/**`.
-- Se limpian las rutas heredadas para que la app muestre el placeholder en
-- lugar de pedir archivos que no existen.
--
-- Es idempotente: volver a aplicarla no cambia nada.

update public.categories set image = null where image like 'assets/%';
update public.products set image = null where image like 'assets/%';
update public.restaurant set image = null where image like 'assets/%';

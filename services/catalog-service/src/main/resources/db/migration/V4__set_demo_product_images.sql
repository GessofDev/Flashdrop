-- Imagenes de demo: la columna `image` guarda la clave del objeto en el
-- bucket privado (`products/<anio>/<mes>/<uuid>.<ext>`) y el catalogo la
-- sirve por `/catalog/images/**`.
--
-- Los assets viven en `infra/demo-images/` y se suben al bucket con
-- `bash infra/demo-images/upload.sh` (ver `infra/demo-images/README.md`).
-- Las claves de esta migracion son las mismas que usan los archivos.
--
-- Es idempotente: volver a aplicarla deja las mismas claves.

update public.products set image = 'products/2026/11/fda64c8e-8b5c-41c9-b3f4-73cb88bfa83f.png'
  where id = 1 and name = 'Burger doble';

update public.products set image = 'products/2026/11/5f295eae-97dd-4acc-bbea-1a2993fb250d.png'
  where id = 2 and name = 'Papas cheddar';

update public.products set image = 'products/2026/11/5793b271-6922-4c87-ac48-d997bacf54b9.png'
  where id = 3 and name = 'Pizza Maipu familiar';

update public.products set image = 'products/2026/11/91c85abb-79fb-4809-9625-62f3cf474663.png'
  where id = 4 and name = 'Bebida 500 cc';

update public.products set image = 'products/2026/11/040bfd80-c114-42a9-9c94-8df47f5f1b0a.png'
  where id = 5 and name = 'Combo Flash';

update public.products set image = 'products/2026/11/d59c887a-ca1f-48b2-92f8-8c70f956b258.png'
  where id = 6 and name = 'Burger Maipu';

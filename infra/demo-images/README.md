# Imagenes de demo del catalogo

Los productos de la demo necesitan una picture. El catalogo no guarda un
archivo: guarda la **clave del objeto** en el bucket privado
`flashdrop-products` (`products/<anio>/<mes>/<uuid>.<ext>`) y la sirve por
`/catalog/images/**`, asi que la app las pide al gateway y no al host
estatico.

## Que hay aqui

| Archivo | Que es |
|---|---|
| `generate.py` | genera las ilustraciones de los 6 platos de la demo |
| `products/2026/11/*.png` | las imagenes, con la clave que usan el bucket y la base |
| `upload.sh` | las sube al bucket (endpoint de FloCI) |

## Subirlas

En el VPS, con el repo actualizado:

```bash
cd ~/flashdrop_backend
bash infra/demo-images/upload.sh
```

El script crea el bucket si no existe y sincroniza `products/` a
`s3://flashdrop-products/products/`, de modo que la clave local
`products/2026/11/<uuid>.png` es exactamente la clave en el bucket.

## Como quedan apuntadas en el catalogo

La migracion `V4__set_demo_product_images.sql` pone esas claves en la
columna `image` de los productos de la demo. Corre sola en el arranque del
servicio (Flyway), asi que el orden es: subir las imagenes primero, y
despues reiniciar el catalog.

## Cambiar las imagenas

Estas ilustraciones son marcadores de posicion, no fotos. Para poner fotos
reales el camino es el del dueno del local:

1. `POST /api/catalog/my/products/image` con el archivo (multipart `file`).
2. El backend responde `{ objectKey, url }`; el `objectKey` es el valor de
   `image` del producto.
3. `PUT /api/catalog/my/products/{id}` con ese `objectKey`.

Ese camino genera la clave con un UUID propio y no toca los assets de esta
carpeta, asi que las dos cosas conviven.

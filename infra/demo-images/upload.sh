#!/usr/bin/env bash
# Sube las imagenes de demo del catalogo al bucket privado.
#
# El catalogo guarda en la columna `image` la **clave del objeto**
# (`products/<anio>/<mes>/<uuid>.<ext>`) y la sirve por
# `/catalog/images/**`. Este script sube los assets con la misma clave que
# usa la migracion `V4__set_demo_product_images.sql`, asi que el catalogo y
# el bucket siempre coinciden.
#
# Uso (en el VPS, con el repo pulled):
#   bash infra/demo-images/upload.sh
#
# Variables opcionales:
#   FLASHDROP_IMAGES_BUCKET  nombre del bucket (flashdrop-products)
#   S3_ENDPOINT              endpoint de S3 (http://127.0.0.1:4566 en el host)

set -euo pipefail

BUCKET="${FLASHDROP_IMAGES_BUCKET:-flashdrop-products}"
ENDPOINT="${S3_ENDPOINT:-http://127.0.0.1:4566}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"

# El bucket es privado y puede no existir todavia (INFRASTRUCTURE.md §8).
aws --endpoint-url "$ENDPOINT" s3api create-bucket \
  --bucket "$BUCKET" --region "$AWS_DEFAULT_REGION" > /dev/null 2>&1 || true

aws --endpoint-url "$ENDPOINT" s3 sync "$HERE/products/" "s3://$BUCKET/products/"

echo "Imagenes de demo en s3://$BUCKET/products/:"
aws --endpoint-url "$ENDPOINT" s3 ls "s3://$BUCKET/products/" --recursive

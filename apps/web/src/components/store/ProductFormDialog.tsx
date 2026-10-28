import { useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';

import { asApiError, storeCatalogApi } from '@/api';
import type { ApiError, CategoryResponse, OwnerProductPayload, ProductResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { CatalogImage } from '@/components/marketplace';
import { Button } from '@/components/ui';

export interface ProductFormDialogProps {
  /** Whether the dialog creates a product or edits an existing one. */
  mode: 'create' | 'edit';
  /** Product the dialog starts with; `null` when it creates a new one. */
  product: ProductResponse | null;
  /** Categories the product can belong to. */
  categories: CategoryResponse[];
  /** Close the dialog. */
  onClose: () => void;
  /** Announce that the backend accepted the change. */
  onSaved: (product: ProductResponse) => void;
}

interface FormValues {
  categoryId: string;
  name: string;
  description: string;
  price: string;
  available: boolean;
  /** Key of the object of the picture uploaded in this session. */
  imageObjectKey: string | null;
}

const INPUT_CLASSES =
  'h-10 w-full rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted';

const LABEL_CLASSES = 'text-sm font-semibold text-foreground';

/**
 * Values the form starts with.
 *
 * The dialog of a new product starts empty; the dialog of an existing one
 * starts with what the catalog answered with.
 */
function initialValues(product: ProductResponse | null): FormValues {
  return {
    categoryId: product === null ? '' : String(product.categoryId),
    name: product?.name ?? '',
    description: product?.description ?? '',
    price: product === null ? '' : String(product.price),
    available: product?.available ?? true,
    imageObjectKey: null,
  };
}

/**
 * Body of the creation of a product.
 *
 * The service derives the owner from the token, so the body never carries
 * the `restaurantId`.
 */
function creationPayload(values: FormValues): OwnerProductPayload {
  const payload: OwnerProductPayload = { available: values.available };

  if (values.categoryId !== '') {
    payload.categoryId = Number(values.categoryId);
  }

  if (values.name.trim() !== '') {
    payload.name = values.name.trim();
  }

  if (values.description.trim() !== '') {
    payload.description = values.description.trim();
  }

  if (values.price.trim() !== '') {
    payload.price = Number(values.price);
  }

  if (values.imageObjectKey !== null) {
    payload.image = values.imageObjectKey;
  }

  return payload;
}

/**
 * Body of the replacement of a product.
 *
 * The endpoint is a full replacement: it demands `categoryId`, `name`,
 * `price` and `available`, and a field that does not travel keeps the value
 * it has.
 *
 * The form does not change the availability — the switch of the row does —
 * so the body carries the availability the product has. When the user empties
 * every field the body goes empty on purpose: the backend answers
 * "Debe enviar al menos un campo para actualizar" (spec S11).
 */
function replacementPayload(values: FormValues, product: ProductResponse): OwnerProductPayload {
  const payload: OwnerProductPayload = {};

  if (values.categoryId !== '') {
    payload.categoryId = Number(values.categoryId);
  }

  if (values.name.trim() !== '') {
    payload.name = values.name.trim();
  }

  if (values.description.trim() !== '') {
    payload.description = values.description.trim();
  }

  if (values.price.trim() !== '') {
    payload.price = Number(values.price);
  }

  if (values.imageObjectKey !== null) {
    payload.image = values.imageObjectKey;
  }

  if (Object.keys(payload).length === 0) {
    return payload;
  }

  payload.available = product.available;

  return payload;
}

/**
 * Drawer of the creation and the edition of a product.
 *
 * The picture is a step of its own: the file travels to
 * `POST /api/catalog/my/products/image`, which answers with the key of the
 * object, and that key is the `image` of the product.
 */
export function ProductFormDialog({
  mode,
  product,
  categories,
  onClose,
  onSaved,
}: ProductFormDialogProps) {
  const [values, setValues] = useState<FormValues>(() => initialValues(product));
  const [pictureUrl, setPictureUrl] = useState<string | null>(product?.image ?? null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<ApiError | null>(null);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const isEdit = mode === 'edit';
  const title = isEdit ? 'Editar producto' : 'Nuevo producto';

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>): Promise<void> => {
    const file = event.target.files?.[0];

    if (file === undefined) {
      return;
    }

    setUploading(true);
    setUploadError(null);

    try {
      const uploaded = await storeCatalogApi.uploadImage(file);

      setValues((current) => ({ ...current, imageObjectKey: uploaded.objectKey }));
      setPictureUrl(uploaded.url);
    } catch (cause: unknown) {
      setUploadError(asApiError(cause));
      setValues((current) => ({ ...current, imageObjectKey: null }));
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();

    setSubmitting(true);
    setSubmitError(null);

    try {
      const saved = isEdit && product !== null
        ? await storeCatalogApi.updateProduct(
            product.id,
            replacementPayload(values, product),
          )
        : await storeCatalogApi.createProduct(creationPayload(values));

      onSaved(saved);
    } catch (cause: unknown) {
      setSubmitError(asApiError(cause));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-foreground/40">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="flex h-full w-full max-w-xl flex-col gap-5 overflow-y-auto bg-surface p-6 shadow-card"
      >
        <header className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <h2 className="font-display text-xl font-bold text-foreground">{title}</h2>
            <p className="text-sm text-foreground-secondary">
              {isEdit
                ? 'Los cambios se guardan en el catálogo de tu tienda.'
                : 'El plato se publica en el catálogo de tu tienda.'}
            </p>
          </div>
          <Button variant="secondary" size="sm" aria-label="Cerrar" onClick={onClose}>
            Cerrar
          </Button>
        </header>

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label className={LABEL_CLASSES} htmlFor="product-image">
              Imagen del producto
            </label>
            <input
              id="product-image"
              name="image"
              type="file"
              accept="image/jpeg, image/png, image/webp"
              onChange={(event) => {
                void handleFileChange(event);
              }}
              className="text-sm text-foreground-secondary file:mr-3 file:rounded-control file:border-0 file:bg-canvas file:px-3 file:py-2 file:text-sm file:font-semibold file:text-foreground"
            />
            {pictureUrl !== null ? (
              <div className="flex items-center gap-3">
                <div className="h-16 w-16 overflow-hidden rounded-control bg-surface-hover">
                  <CatalogImage src={pictureUrl} alt="Imagen del producto" />
                </div>
                {values.imageObjectKey !== null ? (
                  <span className="text-xs font-semibold text-success">Imagen lista</span>
                ) : null}
              </div>
            ) : null}
            {uploading ? <span className="text-xs text-foreground-muted">Subiendo imagen...</span> : null}
            {uploadError !== null ? <ApiErrorNotice error={uploadError} /> : null}
          </div>

          <div className="flex flex-col gap-1.5">
            <label className={LABEL_CLASSES} htmlFor="product-category">
              Categoría
            </label>
            <select
              id="product-category"
              name="categoryId"
              value={values.categoryId}
              onChange={(event) =>
                setValues((current) => ({ ...current, categoryId: event.target.value }))
              }
              className={INPUT_CLASSES}
            >
              <option value="">Sin categoría</option>
              {categories.map((category) => (
                <option key={category.id} value={String(category.id)}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className={LABEL_CLASSES} htmlFor="product-name">
              Nombre del producto
            </label>
            <input
              id="product-name"
              name="name"
              type="text"
              maxLength={100}
              value={values.name}
              onChange={(event) => setValues((current) => ({ ...current, name: event.target.value }))}
              className={INPUT_CLASSES}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className={LABEL_CLASSES} htmlFor="product-description">
              Descripción
            </label>
            <textarea
              id="product-description"
              name="description"
              rows={3}
              maxLength={255}
              value={values.description}
              onChange={(event) =>
                setValues((current) => ({ ...current, description: event.target.value }))
              }
              className={`${INPUT_CLASSES} h-auto py-2`}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className={LABEL_CLASSES} htmlFor="product-price">
              Precio (CLP)
            </label>
            <input
              id="product-price"
              name="price"
              type="text"
              inputMode="numeric"
              value={values.price}
              onChange={(event) => setValues((current) => ({ ...current, price: event.target.value }))}
              className={`${INPUT_CLASSES} nums-tabular`}
            />
            <span className="text-xs text-foreground-muted">
              El monto en pesos, sin puntos ni símbolos.
            </span>
          </div>

          <label className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <input
              type="checkbox"
              name="available"
              checked={values.available}
              onChange={(event) =>
                setValues((current) => ({ ...current, available: event.target.checked }))
              }
              className="h-4 w-4 rounded-control border-border-strong"
            />
            Producto disponible
          </label>

          {submitError !== null ? <ApiErrorNotice error={submitError} /> : null}

          <div className="flex items-center justify-end gap-3 border-t border-border pt-4">
            <Button variant="secondary" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" disabled={submitting || uploading}>
              {isEdit ? 'Guardar cambios' : 'Guardar producto'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

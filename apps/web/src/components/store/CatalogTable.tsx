import type { CategoryResponse, ProductResponse } from '@/api';
import { CatalogImage } from '@/components/marketplace';
import { Button } from '@/components/ui';
import { formatClp, MISSING_VALUE } from '@/lib/format';

import { AvailabilitySwitch } from './AvailabilitySwitch';

export interface CatalogTableProps {
  /** Products the owner endpoint answered with. */
  products: ProductResponse[];
  /** Categories of the catalog, to name the category of every product. */
  categories: CategoryResponse[];
  /** Product whose availability is being changed, if any. */
  pendingProductId: number | null;
  /** Ask for the change of the availability of a product. */
  onToggle: (product: ProductResponse) => void;
  /** Open the form to edit a product. */
  onEdit: (product: ProductResponse) => void;
  /** Open the form with a copy of a product. */
  onDuplicate: (product: ProductResponse) => void;
  /** Ask for the removal of a product. */
  onDelete: (product: ProductResponse) => void;
}

/**
 * Table of the catalog of the store.
 *
 * It is built from the mockup `gesti_n_de_cat_logo_y_men_mi_tienda`: the
 * picture and the identifier of the product, its name and its description,
 * the category, the price in Chilean pesos, the switch of the availability
 * and the actions of the row.
 *
 * The columns of sales and margin of the mockup are not here: the catalog
 * endpoint does not answer them, and the app never invents numbers.
 */
export function CatalogTable({
  products,
  categories,
  pendingProductId,
  onToggle,
  onEdit,
  onDuplicate,
  onDelete,
}: CatalogTableProps) {
  const categoryNames = new Map<number, string>();

  for (const category of categories) {
    categoryNames.set(category.id, category.name);
  }

  return (
    <div className="overflow-x-auto">
      <table aria-label="Catálogo de productos" className="w-full text-left text-sm">
        <thead className="border-b border-border text-xs uppercase tracking-label text-foreground-muted">
          <tr>
            <th scope="col" className="px-4 py-3 font-semibold">
              Producto
            </th>
            <th scope="col" className="px-4 py-3 font-semibold">
              Categoría
            </th>
            <th scope="col" className="px-4 py-3 font-semibold">
              Precio (CLP)
            </th>
            <th scope="col" className="px-4 py-3 font-semibold">
              Disponibilidad
            </th>
            <th scope="col" className="px-4 py-3 text-right font-semibold">
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {products.map((product) => (
            <tr key={product.id} className="align-middle hover:bg-canvas">
              <td className="px-4 py-3">
                <div className="flex items-center gap-3">
                  <div className="h-14 w-14 shrink-0 overflow-hidden rounded-control bg-surface-hover">
                    <CatalogImage src={product.image} alt={product.name} />
                  </div>
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <span className="label-code text-xs text-foreground-muted">ID {product.id}</span>
                    <span className="font-display font-bold text-foreground">{product.name}</span>
                    {product.description.length > 0 ? (
                      <span className="line-clamp-2 text-xs text-foreground-secondary">
                        {product.description}
                      </span>
                    ) : null}
                  </div>
                </div>
              </td>
              <td className="px-4 py-3">
                <span className="inline-flex items-center rounded-control bg-canvas px-2.5 py-1 text-xs font-semibold text-foreground-secondary">
                  {categoryNames.get(product.categoryId) ?? MISSING_VALUE}
                </span>
              </td>
              <td className="px-4 py-3">
                <span className="nums-tabular font-display text-base font-bold text-foreground">
                  {formatClp(product.price)}
                </span>
              </td>
              <td className="px-4 py-3">
                <div className="flex flex-col items-start gap-1">
                  <AvailabilitySwitch
                    product={product}
                    pending={pendingProductId === product.id}
                    onToggle={onToggle}
                  />
                  <span
                    className={
                      product.available
                        ? 'text-xs font-semibold text-success'
                        : 'text-xs font-semibold text-destructive'
                    }
                  >
                    {product.available ? 'Disponible' : 'Agotado'}
                  </span>
                </div>
              </td>
              <td className="px-4 py-3">
                <div className="flex items-center justify-end gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    aria-label={`Editar ${product.name}`}
                    onClick={() => onEdit(product)}
                  >
                    Editar
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    aria-label={`Duplicar ${product.name}`}
                    onClick={() => onDuplicate(product)}
                  >
                    Duplicar
                  </Button>
                  <Button
                    variant="destructive"
                    size="sm"
                    aria-label={`Eliminar ${product.name}`}
                    onClick={() => onDelete(product)}
                  >
                    Eliminar
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

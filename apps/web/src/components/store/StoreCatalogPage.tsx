import { useEffect, useMemo, useState } from 'react';

import { asApiError, catalogApi, storeCatalogApi } from '@/api';
import type { ApiError, CategoryResponse, ProductResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button, Card, Skeleton } from '@/components/ui';

import { CatalogTable } from './CatalogTable';
import { ProductFormDialog } from './ProductFormDialog';

type AvailabilityFilter = 'all' | 'available' | 'out';

interface CatalogState {
  /** Products the owner endpoint answered with. */
  products: ProductResponse[];
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the call is still in flight. */
  loading: boolean;
}

interface DialogState {
  /** Whether the dialog creates a product or edits an existing one. */
  mode: 'create' | 'edit';
  /** Product the dialog starts with; `null` when it creates a new one. */
  product: ProductResponse | null;
}

const FILTERS: ReadonlyArray<{ value: AvailabilityFilter; label: string }> = [
  { value: 'all', label: 'Todos' },
  { value: 'available', label: 'Solo disponibles' },
  { value: 'out', label: 'Solo agotados' },
];

/**
 * "Catálogo y menú" of the store (spec S11).
 *
 * The screen is built from the mockup `gesti_n_de_cat_logo_y_men_mi_tienda`:
 * the summary of the catalog, the filters by category and by availability,
 * the table of the products with the switch of the availability and the
 * actions of every row, and the drawer of the creation and the edition.
 *
 * The endpoint of the owner answers with the products of the restaurant of
 * the token, including the ones that are not available: that is what lets the
 * store reactivate a dish.
 */
export function StoreCatalogPage() {
  const [state, setState] = useState<CatalogState>({ products: [], error: null, loading: true });
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [categoryFilter, setCategoryFilter] = useState('');
  const [availabilityFilter, setAvailabilityFilter] = useState<AvailabilityFilter>('all');
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pendingProductId, setPendingProductId] = useState<number | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;

    setState({ products: [], error: null, loading: true });

    storeCatalogApi
      .listMyProducts()
      .then((products) => {
        if (active) {
          setState({ products, error: null, loading: false });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setState({ products: [], error: asApiError(cause), loading: false });
        }
      });

    return () => {
      active = false;
    };
  }, [reloadToken]);

  useEffect(() => {
    let active = true;

    catalogApi
      .listCategories()
      .then((answer) => {
        if (active) {
          setCategories(answer);
        }
      })
      .catch(() => {
        if (active) {
          setCategories([]);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const reload = (): void => {
    setReloadToken((token) => token + 1);
  };

  const availableCount = useMemo(
    () => state.products.filter((product) => product.available).length,
    [state.products],
  );

  const categoryCounts = useMemo(() => {
    const counts = new Map<number, number>();

    for (const product of state.products) {
      counts.set(product.categoryId, (counts.get(product.categoryId) ?? 0) + 1);
    }

    return counts;
  }, [state.products]);

  const visibleProducts = useMemo(() => {
    const needle = search.trim().toLowerCase();

    return state.products.filter((product) => {
      if (categoryFilter !== '' && product.categoryId !== Number(categoryFilter)) {
        return false;
      }

      if (availabilityFilter === 'available' && !product.available) {
        return false;
      }

      if (availabilityFilter === 'out' && product.available) {
        return false;
      }

      if (needle.length > 0 && !product.name.toLowerCase().includes(needle)) {
        return false;
      }

      return true;
    });
  }, [state.products, categoryFilter, availabilityFilter, search]);

  /**
   * Change the availability of a product.
   *
   * There is no `PATCH` in the endpoint: the change travels as a full
   * replacement of the product, and the state the table shows is the one the
   * backend answered with. A rejected change leaves the product as it was.
   */
  const handleToggle = async (product: ProductResponse): Promise<void> => {
    setPendingProductId(product.id);
    setActionError(null);
    setNotice(null);

    try {
      const updated = await storeCatalogApi.updateProduct(product.id, {
        categoryId: product.categoryId,
        name: product.name,
        price: product.price,
        available: !product.available,
      });

      setState((current) => ({
        ...current,
        products: current.products.map((item) => (item.id === updated.id ? updated : item)),
      }));
      setNotice(`Disponibilidad de ${updated.name} actualizada`);
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
    } finally {
      setPendingProductId(null);
    }
  };

  const handleDelete = async (product: ProductResponse): Promise<void> => {
    const confirmed = window.confirm(`¿Eliminar “${product.name}” del catálogo?`);

    if (!confirmed) {
      return;
    }

    setActionError(null);
    setNotice(null);

    try {
      await storeCatalogApi.deleteProduct(product.id);
      setNotice(`Producto eliminado: ${product.name}`);
      reload();
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
    }
  };

  const handleSaved = (saved: ProductResponse): void => {
    setDialog(null);
    setActionError(null);
    setNotice(`Producto guardado: ${saved.name}`);
    reload();
  };

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="flex flex-col gap-1">
          <span className="text-xs font-semibold uppercase tracking-label text-foreground-muted">
            Total de productos
          </span>
          <span className="nums-tabular font-display text-2xl font-bold text-foreground">
            {state.products.length}
          </span>
          <span className="text-xs text-foreground-secondary">Platos en tu catálogo</span>
        </Card>
        <Card className="flex flex-col gap-1">
          <span className="text-xs font-semibold uppercase tracking-label text-foreground-muted">
            Productos disponibles
          </span>
          <span className="nums-tabular font-display text-2xl font-bold text-success">
            {availableCount}
          </span>
          <span className="text-xs text-foreground-secondary">Visibles en el marketplace</span>
        </Card>
        <Card className="flex flex-col gap-1">
          <span className="text-xs font-semibold uppercase tracking-label text-foreground-muted">
            Productos agotados
          </span>
          <span className="nums-tabular font-display text-2xl font-bold text-destructive">
            {state.products.length - availableCount}
          </span>
          <span className="text-xs text-foreground-secondary">Sin stock ahora</span>
        </Card>
        <Card className="flex flex-col gap-1">
          <span className="text-xs font-semibold uppercase tracking-label text-foreground-muted">
            Categorías del catálogo
          </span>
          <span className="nums-tabular font-display text-2xl font-bold text-foreground">
            {categories.length}
          </span>
          <span className="text-xs text-foreground-secondary">Categorías del menú</span>
        </Card>
      </div>

      {actionError !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={actionError} />
        </div>
      ) : null}

      {notice !== null ? (
        <p role="status" className="mb-6 text-sm font-medium text-success">
          {notice}
        </p>
      ) : null}

      <Card className="mb-6 flex flex-col gap-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-3">
            <span
              aria-hidden="true"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-control bg-primary-surface text-primary"
            >
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M4 5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5Zm4 4h8M8 13h8"
                />
              </svg>
            </span>
            <div className="flex flex-col gap-0.5">
              <h1 className="font-display text-2xl font-bold text-foreground">
                Gestión de Catálogo y Menú
              </h1>
              <p className="text-sm text-foreground-secondary">
                Administra los platos, los precios y la disponibilidad de tu tienda.
              </p>
            </div>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <input
              type="search"
              aria-label="Buscar por nombre de plato"
              placeholder="Buscar por nombre de plato"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="h-10 rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted"
            />
            <Button
              onClick={() => {
                setActionError(null);
                setNotice(null);
                setDialog({ mode: 'create', product: null });
              }}
            >
              Nuevo producto
            </Button>
          </div>
        </div>

        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-foreground" htmlFor="catalog-category-filter">
              Filtrar por categoría
            </label>
            <select
              id="catalog-category-filter"
              value={categoryFilter}
              onChange={(event) => setCategoryFilter(event.target.value)}
              className="h-10 rounded-control border border-border bg-surface px-3 text-sm text-foreground"
            >
              <option value="">Todas las categorías</option>
              {categories.map((category) => (
                <option key={category.id} value={String(category.id)}>
                  {category.name} ({categoryCounts.get(category.id) ?? 0})
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Filtrar por estado">
            {FILTERS.map((filter) => (
              <Button
                key={filter.value}
                variant={availabilityFilter === filter.value ? 'primary' : 'secondary'}
                size="sm"
                aria-pressed={availabilityFilter === filter.value}
                onClick={() => setAvailabilityFilter(filter.value)}
              >
                {filter.label}
              </Button>
            ))}
          </div>
        </div>
      </Card>

      <Card className="p-0">
        {state.loading ? (
          <div className="p-5">
            <Skeleton label="Cargando catálogo" blocks={3} height="h-16" />
          </div>
        ) : state.error !== null ? (
          <div className="p-5">
            <ApiErrorNotice error={state.error} />
          </div>
        ) : state.products.length === 0 ? (
          <p className="p-8 text-center text-sm text-foreground-secondary">
            Tu catálogo está vacío
          </p>
        ) : visibleProducts.length === 0 ? (
          <p className="p-8 text-center text-sm text-foreground-secondary">
            Ningún producto coincide con el filtro
          </p>
        ) : (
          <CatalogTable
            products={visibleProducts}
            categories={categories}
            pendingProductId={pendingProductId}
            onToggle={(product) => {
              void handleToggle(product);
            }}
            onEdit={(product) => {
              setActionError(null);
              setDialog({ mode: 'edit', product });
            }}
            onDuplicate={(product) => {
              setActionError(null);
              setDialog({ mode: 'create', product });
            }}
            onDelete={(product) => {
              void handleDelete(product);
            }}
          />
        )}
      </Card>

      {dialog !== null ? (
        <ProductFormDialog
          key={`${dialog.mode}-${dialog.product?.id ?? 'new'}`}
          mode={dialog.mode}
          product={dialog.product}
          categories={categories}
          onClose={() => setDialog(null)}
          onSaved={handleSaved}
        />
      ) : null}
    </div>
  );
}

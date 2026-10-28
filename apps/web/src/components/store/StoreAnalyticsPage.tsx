import { useEffect, useState } from 'react';

import { asApiError, catalogApi, ordersApi, storeCatalogApi } from '@/api';
import type { ApiError, SalesRange, SalesSummaryResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Skeleton } from '@/components/ui';
import { formatClp, formatOrderDate } from '@/lib/format';

const RANGE_OPTIONS: ReadonlyArray<{ value: SalesRange; label: string }> = [
  { value: 'day', label: 'Día' },
  { value: 'week', label: 'Semana' },
  { value: 'month', label: 'Mes' },
];

/**
 * Store sales analytics (spec S14 and S17).
 *
 * Exposes the four canonical metrics of `GET /api/orders/restaurants/{id}/sales-summary`:
 * total orders, total revenue, average ticket, and the top products list.
 *
 * Prohibited items (margins, hourly curves, daily series, bank settlements,
 * payment channels, AI suggestions, stock alerts, satisfaction/stars) are
 * strictly excluded.
 */
export function StoreAnalyticsPage() {
  const [range, setRange] = useState<SalesRange>('week');
  const [restaurantId, setRestaurantId] = useState<number | null>(null);
  const [restaurantError, setRestaurantError] = useState<ApiError | null>(null);
  const [restaurantLoading, setRestaurantLoading] = useState(true);

  const [summary, setSummary] = useState<SalesSummaryResponse | null>(null);
  const [summaryError, setSummaryError] = useState<ApiError | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [reloadToken, setReloadToken] = useState(0);

  // Resolve the restaurant ID of the authenticated store owner.
  useEffect(() => {
    let active = true;

    async function loadRestaurant(): Promise<void> {
      try {
        const products = await storeCatalogApi.listMyProducts();
        if (!active) return;
        const ownedId = products[0]?.restaurantId ?? null;
        if (ownedId !== null) {
          setRestaurantId(ownedId);
          setRestaurantLoading(false);
          return;
        }
        const restaurants = await catalogApi.listRestaurants();
        if (!active) return;
        setRestaurantId(restaurants[0]?.id ?? 1);
        setRestaurantLoading(false);
      } catch (cause: unknown) {
        if (!active) return;
        setRestaurantError(asApiError(cause));
        setRestaurantLoading(false);
      }
    }

    void loadRestaurant();

    return () => {
      active = false;
    };
  }, []);

  // Fetch the sales summary when restaurantId, range or reloadToken changes.
  useEffect(() => {
    if (restaurantId === null) {
      if (!restaurantLoading) {
        setSummaryLoading(false);
      }
      return;
    }

    let active = true;
    setSummaryLoading(true);
    setSummaryError(null);

    ordersApi
      .getSalesSummary(restaurantId, range)
      .then((data) => {
        if (active) {
          setSummary(data);
          setSummaryError(null);
          setSummaryLoading(false);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setSummary(null);
          setSummaryError(asApiError(cause));
          setSummaryLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [range, reloadToken, restaurantId, restaurantLoading]);

  const activeError = restaurantError ?? summaryError;
  const isLoading = restaurantLoading || summaryLoading;

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <p className="label-code text-xs text-primary">Mi Tienda</p>
          <h1 className="font-display text-2xl font-bold text-foreground">Analíticas de ventas</h1>
          <p className="text-sm text-foreground-secondary">
            Indicadores de ventas de la tienda y productos más vendidos.
          </p>
        </div>

        <div
          role="group"
          aria-label="Rango de tiempo"
          className="flex items-center gap-1 rounded-container bg-surface-hover p-1"
        >
          {RANGE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={range === option.value}
              onClick={() => setRange(option.value)}
              className={`rounded-control px-3 py-1.5 text-sm font-semibold transition-colors ${
                range === option.value
                  ? 'bg-primary text-white shadow-card'
                  : 'text-foreground-secondary hover:bg-surface hover:text-foreground'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </header>

      {activeError !== null ? (
        <div className="mb-6">
          <ApiErrorNotice
            error={activeError}
            onRetry={() => {
              setReloadToken((t) => t + 1);
            }}
          />
        </div>
      ) : null}

      {isLoading ? (
        <Skeleton label="Cargando analíticas de ventas" blocks={4} height="h-28" />
      ) : summary !== null ? (
        <div className="flex flex-col gap-8">
          <section
            aria-label="Métricas principales"
            className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
          >
            <div
              role="region"
              aria-label="Métrica de ingresos"
              className="flex flex-col gap-2 rounded-container border border-border bg-surface p-5 shadow-card"
            >
              <span className="label-code text-xs text-foreground-muted">Ingresos totales</span>
              <span className="nums-tabular font-display text-2xl font-bold text-primary">
                {formatClp(summary.totalRevenue)}
              </span>
              <span className="text-xs text-foreground-secondary">En el rango seleccionado</span>
            </div>

            <div
              role="region"
              aria-label="Métrica de pedidos"
              className="flex flex-col gap-2 rounded-container border border-border bg-surface p-5 shadow-card"
            >
              <span className="label-code text-xs text-foreground-muted">Pedidos entregados</span>
              <span className="nums-tabular font-display text-2xl font-bold text-foreground">
                {summary.totalOrders}
              </span>
              <span className="text-xs text-foreground-secondary">Completados con éxito</span>
            </div>

            <div
              role="region"
              aria-label="Métrica de ticket promedio"
              className="flex flex-col gap-2 rounded-container border border-border bg-surface p-5 shadow-card"
            >
              <span className="label-code text-xs text-foreground-muted">Ticket promedio</span>
              <span className="nums-tabular font-display text-2xl font-bold text-foreground">
                {formatClp(summary.averageTicket)}
              </span>
              <span className="text-xs text-foreground-secondary">Promedio por pedido</span>
            </div>

            <div
              role="region"
              aria-label="Período analizado"
              className="flex flex-col gap-2 rounded-container border border-border bg-surface p-5 shadow-card"
            >
              <span className="label-code text-xs text-foreground-muted">Período analizado</span>
              <div className="flex flex-col gap-0.5 text-xs text-foreground-secondary">
                <span>
                  Desde: <span className="font-semibold text-foreground">{formatOrderDate(summary.from)}</span>
                </span>
                <span>
                  Hasta: <span className="font-semibold text-foreground">{formatOrderDate(summary.to)}</span>
                </span>
              </div>
            </div>
          </section>

          <section
            role="region"
            aria-label="Productos más vendidos"
            className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
          >
            <div className="flex items-center justify-between gap-2 border-b border-border pb-4">
              <div>
                <h2 className="font-display text-lg font-bold text-foreground">
                  Productos más vendidos
                </h2>
                <p className="text-xs text-foreground-secondary">
                  Top productos ordenados por cantidad e ingresos.
                </p>
              </div>
              <span className="nums-tabular text-sm font-semibold text-foreground-muted">
                {summary.topProducts.length} productos
              </span>
            </div>

            {summary.topProducts.length === 0 ? (
              <div className="rounded-container border border-dashed border-border bg-canvas p-8 text-center">
                <p className="font-display font-bold text-foreground">
                  No hay ventas registradas en este período
                </p>
                <p className="mt-1 text-sm text-foreground-secondary">
                  Las ventas aparecerán aquí una vez que los pedidos hayan sido entregados.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-border text-xs text-foreground-muted">
                      <th className="py-2.5 pr-4 font-semibold">#</th>
                      <th className="py-2.5 px-4 font-semibold">Producto</th>
                      <th className="py-2.5 px-4 text-right font-semibold">Unidades</th>
                      <th className="py-2.5 pl-4 text-right font-semibold">Ingresos</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {summary.topProducts.map((product, index) => (
                      <tr key={product.productId} className="hover:bg-surface-hover/50">
                        <td className="py-3 pr-4 font-mono text-xs text-foreground-muted">
                          #{index + 1}
                        </td>
                        <td className="py-3 px-4 font-medium text-foreground">
                          {product.productName}
                        </td>
                        <td className="nums-tabular py-3 px-4 text-right text-foreground-secondary">
                          {product.quantitySold} un.
                        </td>
                        <td className="nums-tabular py-3 pl-4 text-right font-semibold text-foreground">
                          {formatClp(product.revenue)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}

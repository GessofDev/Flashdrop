import { useEffect, useState } from 'react';

import { asApiError, storeCatalogApi } from '@/api';
import type { ApiError, OpeningHours, RestaurantResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Badge, Button, Card, Skeleton } from '@/components/ui';

/** Days of the week; the value is the ISO day the backend stores. */
const DAYS = [
  { day: 1, label: 'Lunes' },
  { day: 2, label: 'Martes' },
  { day: 3, label: 'Miércoles' },
  { day: 4, label: 'Jueves' },
  { day: 5, label: 'Viernes' },
  { day: 6, label: 'Sábado' },
  { day: 7, label: 'Domingo' },
] as const;

interface DayRow {
  day: number;
  enabled: boolean;
  opensAt: string;
  closesAt: string;
}

const DEFAULT_OPENS_AT = '09:00';
const DEFAULT_CLOSES_AT = '22:00';

const TIME_INPUT_CLASSES =
  'h-9 rounded-control border border-border bg-surface px-2 text-sm text-foreground disabled:opacity-50';

function toRows(hours: readonly OpeningHours[]): DayRow[] {
  return DAYS.map(({ day }) => {
    const saved = hours.find((item) => item.day === day);

    return {
      day,
      enabled: saved !== undefined,
      opensAt: saved?.opensAt ?? DEFAULT_OPENS_AT,
      closesAt: saved?.closesAt ?? DEFAULT_CLOSES_AT,
    };
  });
}

/**
 * Opening of the store.
 *
 * The switch stops the orders at any moment; the week says when the store
 * attends. Both show what the backend confirmed: a rejected change leaves the
 * screen as it was. Without any day the store depends only on the switch.
 */
export function StoreHoursPage() {
  const [restaurant, setRestaurant] = useState<RestaurantResponse | null>(null);
  const [rows, setRows] = useState<DayRow[]>(() => toRows([]));
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [pending, setPending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    storeCatalogApi
      .getMyRestaurant()
      .then((answer) => {
        if (active) {
          setRestaurant(answer);
          setRows(toRows(answer.hours ?? []));
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setLoadError(asApiError(cause));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const apply = async (
    change: () => Promise<RestaurantResponse>,
    successNotice: string,
  ): Promise<void> => {
    setPending(true);
    setActionError(null);
    setNotice(null);

    try {
      const answer = await change();
      setRestaurant(answer);
      setRows(toRows(answer.hours ?? []));
      setNotice(successNotice);
    } catch (cause) {
      setActionError(asApiError(cause));
    } finally {
      setPending(false);
    }
  };

  const updateRow = (day: number, patch: Partial<DayRow>): void => {
    setRows((current) =>
      current.map((row) => (row.day === day ? { ...row, ...patch } : row)),
    );
  };

  const handleToggle = (acceptingOrders: boolean): void => {
    void apply(
      () => storeCatalogApi.setOpenStatus(acceptingOrders),
      acceptingOrders ? 'El local volvió a recibir pedidos.' : 'El local dejó de recibir pedidos.',
    );
  };

  const handleSave = (): void => {
    const hours = rows
      .filter((row) => row.enabled)
      .map(({ day, opensAt, closesAt }) => ({ day, opensAt, closesAt }));

    void apply(() => storeCatalogApi.setHours(hours), 'Horario guardado.');
  };

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 md:px-8">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
          Horario y apertura
        </h1>
        <p className="text-sm text-foreground-secondary">
          Define cuándo atiende tu local. Fuera de este horario los clientes no pueden pedir.
        </p>
      </header>

      {loadError !== null ? <ApiErrorNotice error={loadError} /> : null}
      {actionError !== null ? <ApiErrorNotice error={actionError} /> : null}

      {notice !== null ? (
        <p role="status" className="text-sm font-medium text-success">
          {notice}
        </p>
      ) : null}

      {loading ? <Skeleton label="Cargando horario" blocks={2} height="h-40" /> : null}

      {restaurant !== null ? (
        <>
          <Card className="flex items-center justify-between gap-4 p-5">
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <h2 className="font-display text-base font-bold text-foreground">
                  {restaurant.name}
                </h2>
                <Badge variant={restaurant.open === false ? 'destructive' : 'success'}>
                  {restaurant.open === false ? 'Cerrado ahora' : 'Abierto ahora'}
                </Badge>
              </div>
              <p className="text-sm text-foreground-secondary">
                Apágalo para dejar de recibir pedidos aunque estés dentro del horario.
              </p>
            </div>
            <label className="inline-flex items-center">
              <input
                type="checkbox"
                className="peer sr-only"
                aria-label="Recibir pedidos"
                checked={restaurant.acceptingOrders ?? true}
                disabled={pending}
                onChange={(event) => handleToggle(event.target.checked)}
              />
              <span
                aria-hidden="true"
                className="relative h-6 w-11 cursor-pointer rounded-full bg-border-strong transition-colors peer-checked:bg-success peer-focus-visible:outline-2 peer-focus-visible:outline-primary peer-focus-visible:outline-offset-2 peer-disabled:cursor-not-allowed peer-disabled:opacity-50 after:absolute after:left-0.5 after:top-0.5 after:h-5 after:w-5 after:rounded-full after:bg-surface after:shadow-card after:transition-transform peer-checked:after:translate-x-5"
              />
            </label>
          </Card>

          <Card className="flex flex-col gap-4 p-5">
            <div className="flex flex-col gap-1">
              <h2 className="font-display text-base font-bold text-foreground">Horario semanal</h2>
              <p className="text-sm text-foreground-secondary">
                Si el cierre es antes que la apertura, el local cierra al día siguiente. Si no marcas
                ningún día, el local depende solo del interruptor.
              </p>
            </div>

            <div className="flex flex-col gap-2">
              {DAYS.map(({ day, label }) => {
                const row = rows.find((item) => item.day === day);

                if (row === undefined) {
                  return null;
                }

                return (
                  <div key={day} className="flex flex-wrap items-center gap-3">
                    <label className="flex w-32 items-center gap-2 text-sm text-foreground">
                      <input
                        type="checkbox"
                        checked={row.enabled}
                        onChange={(event) => updateRow(day, { enabled: event.target.checked })}
                        className="h-4 w-4 rounded-control accent-primary"
                      />
                      {label}
                    </label>
                    <input
                      type="time"
                      aria-label={`Apertura del ${label}`}
                      value={row.opensAt}
                      disabled={!row.enabled}
                      onChange={(event) => updateRow(day, { opensAt: event.target.value })}
                      className={TIME_INPUT_CLASSES}
                    />
                    <span className="text-sm text-foreground-secondary">a</span>
                    <input
                      type="time"
                      aria-label={`Cierre del ${label}`}
                      value={row.closesAt}
                      disabled={!row.enabled}
                      onChange={(event) => updateRow(day, { closesAt: event.target.value })}
                      className={TIME_INPUT_CLASSES}
                    />
                  </div>
                );
              })}
            </div>

            <Button onClick={handleSave} disabled={pending} className="w-fit">
              {pending ? 'Guardando...' : 'Guardar horario'}
            </Button>
          </Card>
        </>
      ) : null}
    </div>
  );
}

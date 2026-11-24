import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';

import { adminApi, asApiError, COURIER_VEHICLES } from '@/api';
import type { ApiError, CourierVehicle, DeliveryTariff } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Skeleton } from '@/components/ui';

/**
 * Admin panel for viewing and replacing the delivery tariff table (`GET|PUT /api/admin/delivery-tariff`, S11).
 */
export function AdminTariffPanel() {
  const [tariff, setTariff] = useState<DeliveryTariff>({ dimensionRanges: [], distanceRanges: [] });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    adminApi
      .getDeliveryTariff()
      .then((data) => {
        if (active) {
          setTariff(data);
          setLoading(false);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(asApiError(cause));
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const handleSave = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const updated = await adminApi.replaceDeliveryTariff(tariff);
      setTariff(updated);
      setNotice('Tarifa de despacho actualizada.');
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section
      role="region"
      aria-label="Tarifa de despacho"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
    >
      <h2 className="font-display text-lg font-bold text-foreground">Tarifa de despacho</h2>

      {error !== null ? <ApiErrorNotice error={error} /> : null}
      {notice !== null ? <p role="status" className="text-sm font-medium text-success">{notice}</p> : null}

      {loading ? (
        <Skeleton label="Cargando tarifa" blocks={1} height="h-24" />
      ) : (
        <form onSubmit={(e) => void handleSave(e)} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold text-foreground">Tramos por dimensión</h3>
            {tariff.dimensionRanges.map((range, index) => (
              <div key={index} className="flex flex-wrap items-center gap-2">
                <input
                  type="number"
                  aria-label={`Dimensión mínima ${index + 1}`}
                  value={range.minDimension}
                  onChange={(e) => {
                    const next = [...tariff.dimensionRanges];
                    next[index] = { ...range, minDimension: Number(e.target.value) };
                    setTariff({ ...tariff, dimensionRanges: next });
                  }}
                  className="w-24 rounded-control border border-border bg-canvas px-2.5 py-1.5 text-sm"
                />
                <select
                  aria-label={`Vehículo tramo ${index + 1}`}
                  value={range.vehicle}
                  onChange={(e) => {
                    const next = [...tariff.dimensionRanges];
                    next[index] = { ...range, vehicle: e.target.value as CourierVehicle };
                    setTariff({ ...tariff, dimensionRanges: next });
                  }}
                  className="rounded-control border border-border bg-canvas px-2.5 py-1.5 text-sm"
                >
                  {COURIER_VEHICLES.map((v) => (
                    <option key={v} value={v}>{v}</option>
                  ))}
                </select>
                <input
                  type="number"
                  aria-label={`Precio dimensión ${index + 1}`}
                  value={range.price}
                  onChange={(e) => {
                    const next = [...tariff.dimensionRanges];
                    next[index] = { ...range, price: Number(e.target.value) };
                    setTariff({ ...tariff, dimensionRanges: next });
                  }}
                  className="w-28 rounded-control border border-border bg-canvas px-2.5 py-1.5 text-sm"
                />
              </div>
            ))}
          </div>

          <div>
            <button
              type="submit"
              disabled={saving}
              className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-45"
            >
              {saving ? 'Guardando...' : 'Guardar tarifa'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

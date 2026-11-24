import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';

import { adminApi, asApiError } from '@/api';
import type { ApiError, OrderPolicy } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Skeleton } from '@/components/ui';

/**
 * Admin panel for reading and updating global order timing policies (`GET|PUT /api/admin/order-policy`, S11).
 */
export function AdminPolicyPanel() {
  const [policy, setPolicy] = useState<OrderPolicy>({
    paymentExpirationMinutes: 15,
    customerCancelWindowMinutes: 10,
    courierWaitMinutes: 20,
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    adminApi
      .getOrderPolicy()
      .then((data) => {
        if (active) {
          setPolicy(data);
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
      const updated = await adminApi.replaceOrderPolicy(policy);
      setPolicy(updated);
      setNotice('Plazos globales actualizados.');
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section
      role="region"
      aria-label="Política de plazos"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
    >
      <h2 className="font-display text-lg font-bold text-foreground">Política de plazos</h2>

      {error !== null ? <ApiErrorNotice error={error} /> : null}
      {notice !== null ? <p role="status" className="text-sm font-medium text-success">{notice}</p> : null}

      {loading ? (
        <Skeleton label="Cargando política" blocks={1} height="h-24" />
      ) : (
        <form onSubmit={(e) => void handleSave(e)} className="grid gap-4 sm:grid-cols-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-foreground">Vencimiento de pago (min)</span>
            <input
              type="number"
              aria-label="Vencimiento de pago (min)"
              value={policy.paymentExpirationMinutes}
              onChange={(e) =>
                setPolicy({ ...policy, paymentExpirationMinutes: Number(e.target.value) })
              }
              disabled={saving}
              className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-foreground">Ventana cancelación cliente (min)</span>
            <input
              type="number"
              aria-label="Ventana cancelación cliente (min)"
              value={policy.customerCancelWindowMinutes}
              onChange={(e) =>
                setPolicy({ ...policy, customerCancelWindowMinutes: Number(e.target.value) })
              }
              disabled={saving}
              className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-foreground">Espera de repartidor (min)</span>
            <input
              type="number"
              aria-label="Espera de repartidor (min)"
              value={policy.courierWaitMinutes}
              onChange={(e) =>
                setPolicy({ ...policy, courierWaitMinutes: Number(e.target.value) })
              }
              disabled={saving}
              className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
            />
          </label>

          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={saving}
              className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-45"
            >
              {saving ? 'Guardando...' : 'Guardar plazos'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

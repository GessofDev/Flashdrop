import { useState } from 'react';
import type { FormEvent } from 'react';

import { adminApi, asApiError, COURIER_VEHICLES } from '@/api';
import type { ApiError, CourierVehicle } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';

/**
 * Admin panel for onboarding a courier profile via `POST /api/delivery/admin/persons` (S11).
 */
export function AdminCouriersPanel() {
  const [userId, setUserId] = useState('');
  const [vehicle, setVehicle] = useState<CourierVehicle>('MOTO');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    const parsed = Number(userId.trim());
    if (!Number.isFinite(parsed) || parsed <= 0) return;

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const created = await adminApi.createDeliveryPerson(parsed, vehicle);
      setNotice(`Perfil de repartidor #${created.id} creado para usuario ${created.userId}.`);
      setUserId('');
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section
      role="region"
      aria-label="Alta de repartidores"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
    >
      <h2 className="font-display text-lg font-bold text-foreground">Alta de repartidores</h2>

      <form onSubmit={(e) => void handleSubmit(e)} className="flex flex-wrap items-center gap-3">
        <input
          type="number"
          aria-label="ID de usuario repartidor"
          placeholder="ID de usuario"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          disabled={saving}
          className="w-40 rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
        />
        <select
          aria-label="Vehículo"
          value={vehicle}
          onChange={(e) => setVehicle(e.target.value as CourierVehicle)}
          disabled={saving}
          className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
        >
          {COURIER_VEHICLES.map((v) => (
            <option key={v} value={v}>
              {v}
            </option>
          ))}
        </select>
        <button
          type="submit"
          disabled={saving || userId.trim() === ''}
          className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-45"
        >
          {saving ? 'Creando...' : 'Crear perfil de repartidor'}
        </button>
      </form>

      {error !== null ? <ApiErrorNotice error={error} /> : null}
      {notice !== null ? <p role="status" className="text-sm font-medium text-success">{notice}</p> : null}
    </section>
  );
}

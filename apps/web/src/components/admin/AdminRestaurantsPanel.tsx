import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';

import { adminApi, asApiError } from '@/api';
import type { AdminRestaurantResponse, ApiError } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Skeleton } from '@/components/ui';

/**
 * Admin panel for listing restaurants, creating new ones, and assigning owners (S11).
 */
export function AdminRestaurantsPanel() {
  const [restaurants, setRestaurants] = useState<AdminRestaurantResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [ownerUserId, setOwnerUserId] = useState('');
  const [creating, setCreating] = useState(false);
  const [ownerDrafts, setOwnerDrafts] = useState<Record<number, string>>({});

  useEffect(() => {
    let active = true;

    adminApi
      .listRestaurants()
      .then((list) => {
        if (active) {
          setRestaurants(list);
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

  const handleCreate = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    if (name.trim() === '') return;

    setCreating(true);
    setError(null);
    setNotice(null);

    try {
      const parsedOwner = ownerUserId.trim() !== '' ? Number(ownerUserId.trim()) : undefined;
      const created = await adminApi.createRestaurant({
        name: name.trim(),
        address: address.trim() !== '' ? address.trim() : undefined,
        ownerUserId: parsedOwner !== undefined && Number.isFinite(parsedOwner) ? parsedOwner : undefined,
      });
      setRestaurants((prev) => [...prev, created]);
      setName('');
      setAddress('');
      setOwnerUserId('');
      setNotice(`Local "${created.name}" creado.`);
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setCreating(false);
    }
  };

  const handleAssignOwner = async (restaurantId: number): Promise<void> => {
    const raw = ownerDrafts[restaurantId]?.trim() ?? '';
    const parsed = Number(raw);
    if (!Number.isFinite(parsed) || parsed <= 0) return;

    setError(null);
    setNotice(null);

    try {
      const updated = await adminApi.assignRestaurantOwner(restaurantId, parsed);
      setRestaurants((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      setNotice(`Dueño asignado al local "${updated.name}".`);
    } catch (cause: unknown) {
      setError(asApiError(cause));
    }
  };

  return (
    <section
      role="region"
      aria-label="Restaurantes"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
    >
      <h2 className="font-display text-lg font-bold text-foreground">Restaurantes</h2>

      <form onSubmit={(e) => void handleCreate(e)} className="grid gap-3 sm:grid-cols-4">
        <input
          type="text"
          aria-label="Nombre del local"
          placeholder="Nombre del local"
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={creating}
          className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
        />
        <input
          type="text"
          aria-label="Dirección del local"
          placeholder="Dirección (opcional)"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
          disabled={creating}
          className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
        />
        <input
          type="number"
          aria-label="ID de dueño inicial"
          placeholder="ID dueño (opcional)"
          value={ownerUserId}
          onChange={(e) => setOwnerUserId(e.target.value)}
          disabled={creating}
          className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
        />
        <button
          type="submit"
          disabled={creating || name.trim() === ''}
          className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-45"
        >
          {creating ? 'Creando...' : 'Crear local'}
        </button>
      </form>

      {error !== null ? <ApiErrorNotice error={error} /> : null}
      {notice !== null ? <p role="status" className="text-sm font-medium text-success">{notice}</p> : null}

      {loading ? (
        <Skeleton label="Cargando restaurantes" blocks={1} height="h-24" />
      ) : (
        <ul className="flex flex-col gap-3">
          {restaurants.map((restaurant) => (
            <li
              key={restaurant.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-border bg-canvas p-3"
            >
              <div className="flex flex-col">
                <span className="font-semibold text-foreground">{restaurant.name}</span>
                <span className="text-xs text-foreground-secondary">
                  ID {restaurant.id} · Dueño: {restaurant.ownerUserId ?? 'Sin asignar'}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  aria-label={`ID de dueño para ${restaurant.name}`}
                  placeholder="ID usuario"
                  value={ownerDrafts[restaurant.id] ?? ''}
                  onChange={(e) =>
                    setOwnerDrafts((prev) => ({ ...prev, [restaurant.id]: e.target.value }))
                  }
                  className="w-32 rounded-control border border-border bg-surface px-2.5 py-1.5 text-sm text-foreground"
                />
                <button
                  type="button"
                  aria-label={`Asignar dueño a ${restaurant.name}`}
                  onClick={() => void handleAssignOwner(restaurant.id)}
                  className="inline-flex h-9 items-center justify-center rounded-control border border-border bg-surface px-3 text-xs font-semibold text-foreground hover:bg-surface-hover"
                >
                  Asignar dueño
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

import { useState } from 'react';
import type { FormEvent } from 'react';

import { adminApi, asApiError } from '@/api';
import type { AdminUserResponse, ApiError } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';

const ROLE_OPTIONS = ['Cliente', 'Restaurante', 'Repartidor'] as const;

/**
 * Admin panel for searching a user by email and assigning their roles (S11).
 */
export function AdminUsersPanel() {
  const [email, setEmail] = useState('');
  const [user, setUser] = useState<AdminUserResponse | null>(null);
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const handleSearch = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    if (email.trim() === '') return;

    setSearching(true);
    setError(null);
    setNotice(null);

    try {
      const found = await adminApi.findUserByEmail(email.trim());
      setUser(found);
      setSelectedRoles(found.roles);
    } catch (cause: unknown) {
      setUser(null);
      setError(asApiError(cause));
    } finally {
      setSearching(false);
    }
  };

  const toggleRole = (role: string): void => {
    setSelectedRoles((prev) =>
      prev.includes(role) ? prev.filter((item) => item !== role) : [...prev, role],
    );
  };

  const handleSaveRoles = async (): Promise<void> => {
    if (user === null) return;

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const result = await adminApi.assignUserRoles(user.id, selectedRoles);
      const updatedRoles = result.roles.map((r) => r.name);
      setUser({ ...user, roles: updatedRoles });
      setSelectedRoles(updatedRoles);
      setNotice('Roles actualizados.');
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section
      role="region"
      aria-label="Usuarios y roles"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
    >
      <h2 className="font-display text-lg font-bold text-foreground">Usuarios y roles</h2>

      <form onSubmit={(e) => void handleSearch(e)} className="flex flex-wrap gap-2">
        <input
          type="email"
          aria-label="Correo del usuario"
          placeholder="usuario@flashdrop.cl"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={searching}
          className="min-w-0 flex-1 rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground"
        />
        <button
          type="submit"
          disabled={searching || email.trim() === ''}
          className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-45"
        >
          {searching ? 'Buscando...' : 'Buscar usuario'}
        </button>
      </form>

      {error !== null ? <ApiErrorNotice error={error} /> : null}
      {notice !== null ? <p role="status" className="text-sm font-medium text-success">{notice}</p> : null}

      {user !== null ? (
        <div className="flex flex-col gap-3 rounded-control border border-border bg-canvas p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-semibold text-foreground">{`${user.name} ${user.lastName}`.trim()}</span>
            <span className="text-xs text-foreground-muted">ID {user.id} · {user.email}</span>
          </div>

          <div className="flex flex-wrap gap-4">
            {ROLE_OPTIONS.map((role) => (
              <label key={role} className="flex items-center gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={selectedRoles.some((r) => r.toLowerCase() === role.toLowerCase())}
                  onChange={() => toggleRole(role)}
                  disabled={saving}
                />
                <span>{role}</span>
              </label>
            ))}
          </div>

          <div>
            <button
              type="button"
              disabled={saving}
              onClick={() => void handleSaveRoles()}
              className="inline-flex h-9 items-center justify-center rounded-control border border-border bg-surface px-3.5 text-sm font-semibold text-foreground hover:bg-surface-hover disabled:opacity-45"
            >
              {saving ? 'Guardando...' : 'Guardar roles'}
            </button>
          </div>
        </div>
      ) : null}
    </section>
  );
}

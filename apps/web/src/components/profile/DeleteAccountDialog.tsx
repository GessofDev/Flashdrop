import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { asApiError, authApi } from '@/api';
import type { ApiError } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button } from '@/components/ui';
import { routePaths } from '@/app/routePaths';
import { useSession } from '@/session';

/**
 * Deletion of the account (spec S9).
 *
 * The backend owns the deletion (`DELETE /auth/account`): it disables the
 * account at once and erases the data of the other services in the
 * background. This block only asks for it and shows what it answers.
 *
 * The deletion is not one click: the confirmation asks for the password of
 * the account, because the backend asks for it, so nobody deletes an account
 * by accident.
 *
 * When the backend accepts the deletion, the session is forgotten and the
 * customer is sent to the login route. When it fails, the literal message of
 * the backend is shown and the account stays.
 */
export function DeleteAccountDialog() {
  const navigate = useNavigate();
  const { clearSession } = useSession();

  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const handleConfirm = async (event: React.FormEvent) => {
    event.preventDefault();

    // The password is what the backend asks for: without it there is nothing
    // to send, so the deletion does not happen.
    if (password.length === 0) {
      return;
    }

    setBusy(true);
    setError(null);

    try {
      await authApi.deleteAccount(password);

      clearSession();
      navigate(routePaths.login);
    } catch (cause: unknown) {
      setError(asApiError(cause));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = () => {
    setOpen(false);
    setPassword('');
    setError(null);
  };

  return (
    <section
      role="region"
      aria-label="Eliminar cuenta"
      className="flex flex-col gap-4 rounded-container border border-destructive/30 bg-surface p-6 shadow-card"
    >
      <div className="flex flex-col gap-1">
        <h2 className="font-display text-lg font-bold text-foreground">Eliminar cuenta</h2>
        <p className="text-xs text-foreground-secondary">
          La cuenta se deshabilita al instante y tus datos se borran en segundo plano.
        </p>
      </div>

      {open ? (
        <form onSubmit={handleConfirm} className="flex max-w-xl flex-col gap-4">
          <p className="text-sm text-foreground">
            Escribe tu contraseña para confirmar. Así nadie puede eliminar tu cuenta por accidente.
          </p>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="delete-account-password" className="text-sm font-semibold text-foreground">
              Contraseña de la cuenta
            </label>
            <input
              id="delete-account-password"
              name="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="h-10 rounded-control border border-border bg-canvas px-3 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-primary"
            />
          </div>

          {/*
            The notice shows the literal message of the backend, the 401 of a
            wrong password included: here the 401 is the answer of the
            deletion and not an expired session, so it must not be hidden.
          */}
          {error !== null ? <ApiErrorNotice error={error} showUnauthorized /> : null}

          <div className="flex items-center gap-3">
            <Button type="submit" variant="destructive" disabled={busy || password.length === 0}>
              Eliminar cuenta
            </Button>
            <Button type="button" variant="secondary" onClick={handleCancel}>
              Cancelar
            </Button>
          </div>
        </form>
      ) : (
        <Button variant="destructive" onClick={() => setOpen(true)}>
          Eliminar mi cuenta
        </Button>
      )}
    </section>
  );
}

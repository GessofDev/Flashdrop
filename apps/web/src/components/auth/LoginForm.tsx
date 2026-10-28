import { useState } from 'react';
import type { FormEvent } from 'react';

import type { LoginRequest } from '@/api';
import { isRememberSessionEnabled } from '@/session/rememberSession';
import { Button } from '@/components/ui';

import { formatRut, isValidRut } from './rut';

/** Identifier the user signs in with. */
type IdentifierMode = 'rut' | 'email';

export interface LoginFormProps {
  /** Called with the credentials and the "remember my session" preference. */
  onSubmit: (credentials: LoginRequest, remember: boolean) => void;
  /** Whether a call is in flight. */
  submitting: boolean;
}

const INPUT_CLASSES =
  'h-11 w-full rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted';

/**
 * Sign-in form of the unified portal.
 *
 * The identifier is the RUT or the email of the account; the password always
 * travels to `POST /auth/login` and never leaves the browser.
 *
 * "Recordar mi sesión" does not persist the token outside `sessionStorage`:
 * it decides whether the app restores the session when the page is reloaded
 * **inside the same tab**. The tokens still die with the tab.
 */
export function LoginForm({ onSubmit, submitting }: LoginFormProps) {
  const [identifierMode, setIdentifierMode] = useState<IdentifierMode>('rut');
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(isRememberSessionEnabled);

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    onSubmit({ login: identifier.trim(), password }, remember);
  };

  const tabClasses = (mode: IdentifierMode): string =>
    [
      'border-b-2 pb-1 text-sm font-semibold transition-colors',
      identifierMode === mode
        ? 'border-primary text-primary'
        : 'border-transparent text-foreground-secondary hover:text-foreground',
    ].join(' ');

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-4">
          <button type="button" className={tabClasses('rut')} onClick={() => setIdentifierMode('rut')}>
            RUT Chileno
          </button>
          <button
            type="button"
            className={tabClasses('email')}
            onClick={() => setIdentifierMode('email')}
          >
            Correo Electrónico
          </button>
          <span className="label-code ml-auto text-[11px] text-foreground-muted">Formato Nacional</span>
        </div>

        {identifierMode === 'rut' ? (
          <>
            <label className="sr-only" htmlFor="login-identifier">
              RUT
            </label>
            <input
              id="login-identifier"
              name="login"
              autoComplete="username"
              placeholder="16.482.915-K"
              value={identifier}
              onChange={(event) => setIdentifier(formatRut(event.target.value))}
              className={INPUT_CLASSES}
            />
            <p className="text-xs text-foreground-secondary">
              Ingresa tu cédula con o sin puntos y guión. Validador Módulo 11{' '}
              {isValidRut(identifier) ? 'validado.' : 'activado.'}
            </p>
          </>
        ) : (
          <>
            <label className="sr-only" htmlFor="login-identifier">
              Correo electrónico
            </label>
            <input
              id="login-identifier"
              name="login"
              type="email"
              autoComplete="username"
              placeholder="nombre@ejemplo.cl"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              className={INPUT_CLASSES}
            />
            <p className="text-xs text-foreground-secondary">
              Usa el correo electrónico con el que creaste tu cuenta en FlashDrop.
            </p>
          </>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between gap-3">
          <label className="text-sm font-semibold text-foreground" htmlFor="login-password">
            Contraseña
          </label>
          <a className="text-xs font-semibold text-primary hover:text-primary-hover" href="#">
            ¿Olvidaste tu contraseña?
          </a>
        </div>
        <input
          id="login-password"
          name="password"
          type="password"
          autoComplete="current-password"
          placeholder="••••••••••"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={INPUT_CLASSES}
        />
      </div>

      <label className="flex items-center gap-2.5 text-sm text-foreground">
        <input
          type="checkbox"
          checked={remember}
          onChange={(event) => setRemember(event.target.checked)}
          className="h-4 w-4 rounded-control accent-primary"
        />
        Recordar mi sesión en este dispositivo
      </label>

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? 'Iniciando sesión...' : 'Iniciar Sesión en FlashDrop'}
      </Button>
    </form>
  );
}

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { ApiError, authApi } from '@/api';
import type { LoginRequest, RegisterRequest } from '@/api';
import { ROLE_HOME } from '@/app/roleRoutes';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { FlashDropMark } from '@/components/shell/FlashDropMark';
import { Button } from '@/components/ui';
import { useSession } from '@/session';
import { setRememberSessionEnabled } from '@/session/rememberSession';
import type { Role } from '@/session';

import { AuthBrandPanel } from './AuthBrandPanel';
import { LoginForm } from './LoginForm';
import { RegisterForm } from './RegisterForm';
import { RoleDestinationPicker } from './RoleDestinationPicker';

/** View of the left panel. */
type AuthMode = 'login' | 'register';

/** Translate an unknown failure into the notice of the form. */
function toApiError(cause: unknown): ApiError {
  if (cause instanceof ApiError) {
    return cause;
  }

  return new ApiError('No se pudo completar la solicitud.', {
    status: 0,
    kind: 'unknown',
    cause,
  });
}

/**
 * Unified portal of access (specs S3 and S5).
 *
 * The left panel holds the tabs, the module of preferred destination and the
 * credentials; the right panel holds the brand. The destination chooses
 * where the user lands after signing in — it never grants a role.
 */
export function LoginPage() {
  const [mode, setMode] = useState<AuthMode>('login');
  const [destination, setDestination] = useState<Role>('CLIENTE');
  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { login } = useSession();
  const navigate = useNavigate();

  const handleLogin = async (credentials: LoginRequest, remember: boolean): Promise<void> => {
    setSubmitting(true);
    setError(null);
    setNotice(null);
    setRememberSessionEnabled(remember);

    try {
      const result = await authApi.login(credentials);
      login(result, { activeRole: destination });
      navigate(ROLE_HOME[destination]);
    } catch (cause) {
      setError(toApiError(cause));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRegister = async (account: RegisterRequest): Promise<void> => {
    setSubmitting(true);
    setError(null);
    setNotice(null);

    try {
      await authApi.register(account);
      setNotice('Cuenta creada. Ya puedes iniciar sesión.');
      setMode('login');
    } catch (cause) {
      setError(toApiError(cause));
    } finally {
      setSubmitting(false);
    }
  };

  const tabClasses = (tab: AuthMode): string =>
    [
      'rounded-control px-4 py-2 text-sm font-semibold transition-colors',
      mode === tab ? 'bg-surface text-foreground shadow-card' : 'text-foreground-secondary hover:text-foreground',
    ].join(' ');

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex h-16 w-full max-w-app items-center justify-between gap-4 px-4 md:px-8">
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-control bg-primary text-white">
              <FlashDropMark className="h-5 w-5" />
            </span>
            <span className="font-display text-lg font-bold text-foreground">
              Flash<span className="text-primary">Drop</span>
            </span>
            <span className="hidden items-center gap-2 rounded-full bg-surface-hover px-3 py-1.5 text-xs text-foreground-secondary md:flex">
              <span className="h-2 w-2 rounded-full bg-success" aria-hidden="true" />
              Conexión segura TLS 1.3
            </span>
          </div>
          <div className="hidden items-center gap-2 rounded-control bg-surface-hover px-3 py-2 text-xs sm:flex">
            <span className="label-code text-foreground-muted">Soporte Chile</span>
            <span className="nums-tabular font-semibold text-foreground">+56 800 914 200</span>
          </div>
        </div>
      </header>

      <main className="flex-1 bg-linear-to-b from-primary-surface/40 via-canvas to-canvas">
        <div className="mx-auto w-full max-w-app px-4 py-8 md:px-6 lg:px-8">
          <div className="grid grid-cols-1 overflow-hidden rounded-overlay bg-surface shadow-card lg:grid-cols-12">
            <div className="flex flex-col gap-6 p-6 sm:p-10 lg:col-span-7 lg:p-12">
              <div className="inline-flex w-fit rounded-container bg-surface-hover p-1">
                <button
                  type="button"
                  className={tabClasses('login')}
                  aria-pressed={mode === 'login'}
                  onClick={() => {
                    setMode('login');
                    setError(null);
                  }}
                >
                  Iniciar Sesión
                </button>
                <button
                  type="button"
                  className={tabClasses('register')}
                  aria-pressed={mode === 'register'}
                  onClick={() => {
                    setMode('register');
                    setError(null);
                    setNotice(null);
                  }}
                >
                  Crear Cuenta
                </button>
              </div>

              <div className="flex flex-col gap-3">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="label-code rounded-full bg-primary-surface px-3 py-1 text-[11px] font-bold text-primary">
                    Acceso Unificado Chile
                  </span>
                  <span className="flex items-center gap-1.5 text-xs font-semibold text-success">
                    <span className="h-2 w-2 rounded-full bg-success" aria-hidden="true" />
                    Servidores Operativos
                  </span>
                </div>
                <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">
                  Bienvenido a tu cuenta{' '}
                  <span className="text-primary">FlashDrop</span>
                </h1>
                <p className="text-sm text-foreground-secondary">
                  Un único perfil chileno validado para ordenar gastronomía, gestionar la cocina de
                  tu local o repartir pedidos por la ciudad.
                </p>
              </div>

              <RoleDestinationPicker value={destination} onChange={setDestination} />

              {error !== null ? <ApiErrorNotice error={error} showUnauthorized /> : null}

              {notice !== null ? (
                <p role="status" className="rounded-control bg-success-surface px-3 py-2 text-sm text-success">
                  {notice}
                </p>
              ) : null}

              {mode === 'login' ? (
                <LoginForm onSubmit={handleLogin} submitting={submitting} />
              ) : (
                <RegisterForm onSubmit={handleRegister} submitting={submitting} />
              )}

              <div className="flex flex-col gap-3">
                <div className="flex items-center gap-3">
                  <span className="h-px flex-1 bg-border" aria-hidden="true" />
                  <span className="text-xs text-foreground-secondary">
                    o accede mediante identidad digital
                  </span>
                  <span className="h-px flex-1 bg-border" aria-hidden="true" />
                </div>

                <p id="identity-not-available" className="text-xs text-foreground-secondary">
                  ClaveÚnica, Google y Apple ID todavía no están disponibles en FlashDrop: el
                  backend no integra proveedores de identidad federada.
                </p>

                <Button
                  variant="secondary"
                  disabled
                  aria-label="Continuar con ClaveÚnica"
                  aria-describedby="identity-not-available"
                  className="w-full justify-between"
                >
                  <span className="flex flex-col items-start text-left">
                    <span>Continuar con ClaveÚnica</span>
                    <span className="text-xs font-normal text-foreground-muted">
                      Gobierno Digital de Chile • Registro Civil
                    </span>
                  </span>
                </Button>

                <div className="grid grid-cols-2 gap-3">
                  <Button
                    variant="secondary"
                    disabled
                    aria-describedby="identity-not-available"
                    className="w-full"
                  >
                    Google
                  </Button>
                  <Button
                    variant="secondary"
                    disabled
                    aria-describedby="identity-not-available"
                    className="w-full"
                  >
                    Apple ID
                  </Button>
                </div>
              </div>

              <div className="rounded-container bg-surface-hover p-4">
                <p className="text-sm font-semibold text-foreground">Servidores Seguros en Santiago</p>
                <p className="mt-1 text-xs text-foreground-secondary">
                  Conexión cifrada TLS 1.3 con nodo local AWS sa-east-1. Cumplimiento con Ley N° 19.628
                  de Protección de Datos Personales de Chile.
                </p>
                <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-xs">
                  <a className="font-semibold text-primary hover:text-primary-hover" href="#">
                    ¿No tienes local o moto? Postula como Partner
                  </a>
                  <span className="text-foreground-secondary">Mesa Central: +56 2 2914 8000</span>
                </div>
              </div>
            </div>

            <div className="bg-foreground p-8 lg:col-span-5 lg:p-12">
              <AuthBrandPanel />
            </div>
          </div>
        </div>
      </main>

      <footer className="border-t border-border bg-surface">
        <div className="mx-auto flex w-full max-w-app flex-col gap-4 px-4 py-6 md:flex-row md:items-center md:justify-between md:px-8">
          <div className="flex flex-wrap items-center gap-2 text-sm text-foreground-secondary">
            <span className="font-semibold text-foreground">Cobertura FlashDrop Chile:</span>
            <span className="rounded-full bg-canvas px-2.5 py-0.5 text-xs">Santiago (RM)</span>
            <span className="rounded-full bg-canvas px-2.5 py-0.5 text-xs">Valparaíso &amp; Viña</span>
            <span className="rounded-full bg-canvas px-2.5 py-0.5 text-xs">Gran Concepción</span>
          </div>
          <div className="flex flex-wrap items-center gap-4 text-sm text-foreground-secondary">
            <a className="hover:text-primary" href="#">
              Términos y Condiciones
            </a>
            <a className="hover:text-primary" href="#">
              Política de Privacidad
            </a>
            <span>© 2025 FlashDrop SpA</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

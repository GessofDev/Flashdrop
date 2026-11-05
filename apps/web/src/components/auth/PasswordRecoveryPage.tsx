import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { ApiError, authApi } from '@/api';
import { routePaths } from '@/app/routePaths';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { FlashDropMark } from '@/components/shell/FlashDropMark';
import { Button } from '@/components/ui';

const INPUT_CLASSES =
  'h-11 w-full rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted';

const MIN_PASSWORD_LENGTH = 10;

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

/** Token of the emailed link. It travels in the fragment, never in the query. */
function readToken(hash: string): string | null {
  return new URLSearchParams(hash.replace(/^#/, '')).get('token');
}

/** Same rules of `PasswordPolicy`, to answer before the call spends the link. */
function passwordProblem(password: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `La contraseña debe tener al menos ${MIN_PASSWORD_LENGTH} caracteres.`;
  }

  if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password)) {
    return 'La contraseña debe incluir mayúscula, minúscula y número.';
  }

  return null;
}

function RequestLinkForm() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      await authApi.forgotPassword(email.trim());
      setSent(true);
    } catch (cause) {
      setError(toApiError(cause));
    } finally {
      setSubmitting(false);
    }
  };

  if (sent) {
    // The copy does not say whether the account exists: the backend hides it too.
    return (
      <p role="status" className="rounded-control bg-success-surface px-3 py-2 text-sm text-success">
        Si el correo está registrado, te enviamos un enlace para crear una nueva contraseña. Revisa
        también la carpeta de spam.
      </p>
    );
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(event) => void handleSubmit(event)} noValidate>
      <p className="text-sm text-foreground-secondary">
        Ingresa el correo de tu cuenta y te enviaremos un enlace para crear una nueva contraseña.
      </p>

      {error !== null ? <ApiErrorNotice error={error} showUnauthorized /> : null}

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-semibold text-foreground" htmlFor="recovery-email">
          Correo electrónico
        </label>
        <input
          id="recovery-email"
          type="email"
          autoComplete="username"
          placeholder="nombre@ejemplo.cl"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={INPUT_CLASSES}
        />
      </div>

      <Button type="submit" disabled={submitting || email.trim() === ''} className="w-full">
        {submitting ? 'Enviando...' : 'Enviar enlace'}
      </Button>
    </form>
  );
}

interface NewPasswordFormProps {
  token: string;
  /** Called when the user wants a new link instead of this one. */
  onRequestNewLink: () => void;
}

function NewPasswordForm({ token, onRequestNewLink }: NewPasswordFormProps) {
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);

    const invalid =
      passwordProblem(password) ??
      (password === confirmation ? null : 'Las contraseñas no coinciden.');
    setProblem(invalid);

    if (invalid !== null) {
      return;
    }

    setSubmitting(true);

    try {
      await authApi.resetPassword(token, password);
      setDone(true);
    } catch (cause) {
      setError(toApiError(cause));
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <div className="flex flex-col gap-4">
        <p role="status" className="rounded-control bg-success-surface px-3 py-2 text-sm text-success">
          Tu contraseña se actualizó. Por seguridad cerramos tus sesiones abiertas.
        </p>
        <Link
          to={routePaths.login}
          className="text-center text-sm font-semibold text-primary hover:text-primary-hover"
        >
          Iniciar sesión
        </Link>
      </div>
    );
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(event) => void handleSubmit(event)} noValidate>
      <p className="text-sm text-foreground-secondary">
        Crea una contraseña de al menos {MIN_PASSWORD_LENGTH} caracteres, con mayúscula, minúscula y
        número.
      </p>

      {error !== null ? (
        <div className="flex flex-col gap-2">
          <ApiErrorNotice error={error} showUnauthorized />
          <button
            type="button"
            onClick={onRequestNewLink}
            className="w-fit text-sm font-semibold text-primary hover:text-primary-hover"
          >
            Solicitar un enlace nuevo
          </button>
        </div>
      ) : null}

      {problem !== null ? (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      ) : null}

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-semibold text-foreground" htmlFor="recovery-password">
          Nueva contraseña
        </label>
        <input
          id="recovery-password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={INPUT_CLASSES}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-semibold text-foreground" htmlFor="recovery-confirmation">
          Repite la contraseña
        </label>
        <input
          id="recovery-confirmation"
          type="password"
          autoComplete="new-password"
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
          className={INPUT_CLASSES}
        />
      </div>

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? 'Guardando...' : 'Guardar contraseña'}
      </Button>
    </form>
  );
}

/**
 * Password recovery, a public route next to the login.
 *
 * Without a token it asks for the email; with the token of the link it asks
 * for the new password. The token leaves the address bar as soon as it is
 * read, so it does not stay in the history of a shared computer.
 */
export function PasswordRecoveryPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [token, setToken] = useState(() => readToken(location.hash));

  // The link may land on a tab already open here: a change of the fragment does
  // not reload the page, so the token has to be read on every change.
  useEffect(() => {
    if (location.hash === '') {
      return;
    }

    const linkToken = readToken(location.hash);

    if (linkToken !== null) {
      setToken(linkToken);
    }

    navigate(location.pathname, { replace: true });
  }, [location.hash, location.pathname, navigate]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas px-4 py-8">
      <main className="flex w-full max-w-md flex-col gap-6 rounded-overlay bg-surface p-6 shadow-card sm:p-10">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 items-center justify-center rounded-control bg-primary text-white">
            <FlashDropMark className="h-5 w-5" />
          </span>
          <span className="font-display text-lg font-bold text-foreground">
            Flash<span className="text-primary">Drop</span>
          </span>
        </div>

        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
          {token === null ? 'Recupera tu contraseña' : 'Crea tu nueva contraseña'}
        </h1>

        {token === null ? (
          <RequestLinkForm />
        ) : (
          <NewPasswordForm token={token} onRequestNewLink={() => setToken(null)} />
        )}

        <Link
          to={routePaths.login}
          className="text-center text-xs font-semibold text-foreground-secondary hover:text-primary"
        >
          Volver al inicio de sesión
        </Link>
      </main>
    </div>
  );
}

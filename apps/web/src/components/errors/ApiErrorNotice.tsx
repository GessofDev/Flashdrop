import type { ApiError } from '@/api/ApiError';
import { resolveApiErrorOutcome } from '@/api/errorPolicy';
import { Button } from '@/components/ui';

export interface ApiErrorNoticeProps {
  /** The failed call; its message is the message of the backend. */
  error: ApiError;
  /** Action of the retry, offered when the error is a conflict. */
  onRetry?: () => void;
}

/**
 * Notice of a failed call.
 *
 * It shows the message of the backend as it comes — never a message of its
 * own — and follows the rules of the spec S7: a conflict offers the retry, a
 * forbidden error only informs, and a 401 renders nothing because the app is
 * being redirected to the login route.
 */
export function ApiErrorNotice({ error, onRetry }: ApiErrorNoticeProps) {
  const outcome = resolveApiErrorOutcome(error);

  if (outcome.kind === 'redirect-to-login') {
    return null;
  }

  return (
    <div
      role="alert"
      className="rounded-container border border-border bg-surface p-4 text-sm text-foreground"
    >
      <p className="text-destructive">{outcome.message}</p>
      {outcome.kind === 'conflict' && onRetry !== undefined ? (
        <Button variant="secondary" size="sm" className="mt-3" onClick={onRetry}>
          Reintentar
        </Button>
      ) : null}
    </div>
  );
}

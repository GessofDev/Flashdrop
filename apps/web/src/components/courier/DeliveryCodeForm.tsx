import { useState } from 'react';

import type { DeliveryRoute } from '@/api/delivery';

/**
 * A delivery code is six digits.
 *
 * The backend rejects any other value with a 400, so the form checks it
 * before spending a call.
 */
const DELIVERY_CODE_PATTERN = /^\d{6}$/;

export interface DeliveryCodeFormProps {
  /** Route whose delivery is being confirmed. */
  route: DeliveryRoute;
  /** Whether the confirmation is in flight. */
  pending: boolean;
  /** Send the code the customer dictated. */
  onSubmit: (code: string) => void;
  /** Give the confirmation up and go back to the route. */
  onCancel: () => void;
}

/**
 * Form that asks the customer for the verification code of a secure order.
 *
 * The courier never sees the code: the customer dictates it and the
 * backend validates it.
 */
export function DeliveryCodeForm({ route, pending, onSubmit, onCancel }: DeliveryCodeFormProps) {
  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);

  const submit = (): void => {
    if (!DELIVERY_CODE_PATTERN.test(code)) {
      setCodeError('El código de entrega tiene 6 dígitos.');

      return;
    }

    setCodeError(null);
    onSubmit(code);
  };

  return (
    <form
      aria-label="Confirmar entrega"
      className="flex flex-col gap-3 rounded-container border border-border bg-surface p-5 shadow-card"
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <div className="flex flex-col gap-1">
        <p className="label-code text-xs text-primary">Entrega segura</p>
        <h3 className="font-display text-lg font-bold text-foreground">
          Confirma la entrega con el código del cliente
        </h3>
        <p className="text-sm text-foreground-secondary">
          {`Pide al cliente el código de 6 dígitos del pedido ${route.code ?? route.orderId}.`}
        </p>
      </div>

      <label htmlFor="delivery-code" className="label-code text-xs text-foreground-muted">
        Código de entrega
      </label>
      <input
        id="delivery-code"
        name="code"
        value={code}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        placeholder="000000"
        className="nums-tabular h-11 rounded-control border border-border bg-canvas px-3 text-sm text-foreground"
        onChange={(event) => {
          setCode(event.target.value.replace(/\D/g, ''));
        }}
      />

      {codeError !== null ? (
        <p role="alert" className="text-sm text-destructive">
          {codeError}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-3">
        <button
          type="submit"
          disabled={!DELIVERY_CODE_PATTERN.test(code) || pending}
          className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white transition-colors hover:bg-primary-hover disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-45"
        >
          Confirmar entrega con código
        </button>
        <button
          type="button"
          className="inline-flex h-10 items-center justify-center rounded-control border border-border px-4 text-sm font-semibold text-foreground-secondary transition-colors hover:bg-surface-hover"
          onClick={onCancel}
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}

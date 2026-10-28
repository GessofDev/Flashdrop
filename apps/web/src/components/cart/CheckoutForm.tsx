import { useState } from 'react';
import type { FormEvent } from 'react';

import type { ApiError } from '@/api';
import { PAYMENT_METHODS } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button } from '@/components/ui';

export interface CheckoutValues {
  /** Delivery address; the backend rejects an empty one. */
  address: string;
  /** Payment method the customer declares. */
  paymentMethod: string;
  /** Estimated distance in kilometres; empty when the customer leaves it out. */
  distanceKm: string;
  /** Estimated minutes of the dispatch; empty when the customer leaves it out. */
  estimatedMinutes: string;
}

export interface CheckoutFormProps {
  /** Send the declared data of the checkout. */
  onSubmit: (values: CheckoutValues) => void | Promise<void>;
  /** Whether the order is being created. */
  submitting: boolean;
  /** Failure of the last attempt, with the message of the backend. */
  error: ApiError | null;
}

const INPUT_CLASSES =
  'h-10 w-full rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted';

/**
 * Form of the checkout.
 *
 * The checkout is declarative: the customer declares the address and the
 * payment method, and the app sends them to `POST /api/orders`. There is no
 * payment gateway behind it, so the form never pretends to charge anything.
 */
export function CheckoutForm({ onSubmit, submitting, error }: CheckoutFormProps) {
  const [address, setAddress] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<string>(PAYMENT_METHODS[0]);
  const [distanceKm, setDistanceKm] = useState('');
  const [estimatedMinutes, setEstimatedMinutes] = useState('');

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();

    onSubmit({ address, paymentMethod, distanceKm, estimatedMinutes });
  };

  const labelClasses = 'text-sm font-semibold text-foreground';

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3 border-t border-border pt-4">
      <div className="flex flex-col gap-1.5">
        <label className={labelClasses} htmlFor="checkout-address">
          Dirección de entrega
        </label>
        <input
          id="checkout-address"
          name="address"
          autoComplete="street-address"
          placeholder="Av. Pedro de Valdivia 1234, Providencia"
          value={address}
          onChange={(event) => setAddress(event.target.value)}
          className={INPUT_CLASSES}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className={labelClasses} htmlFor="checkout-payment-method">
          Método de pago
        </label>
        <select
          id="checkout-payment-method"
          name="payment_method"
          value={paymentMethod}
          onChange={(event) => setPaymentMethod(event.target.value)}
          className={INPUT_CLASSES}
        >
          {PAYMENT_METHODS.map((method) => (
            <option key={method} value={method}>
              {method}
            </option>
          ))}
        </select>
        <p className="text-xs text-foreground-muted">
          El método de pago es declarativo: el pedido registra tu preferencia y todavía no se
          procesa ningún cobro.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-1.5">
          <label className={labelClasses} htmlFor="checkout-distance">
            Distancia estimada (km)
          </label>
          <input
            id="checkout-distance"
            name="distance_km"
            type="number"
            min="0"
            step="0.1"
            value={distanceKm}
            onChange={(event) => setDistanceKm(event.target.value)}
            className={INPUT_CLASSES}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label className={labelClasses} htmlFor="checkout-minutes">
            Tiempo estimado (min)
          </label>
          <input
            id="checkout-minutes"
            name="estimated_minutes"
            type="number"
            min="0"
            step="1"
            value={estimatedMinutes}
            onChange={(event) => setEstimatedMinutes(event.target.value)}
            className={INPUT_CLASSES}
          />
        </div>
      </div>
      <p className="text-xs text-foreground-muted">
        La distancia y el tiempo son opcionales: si los dejas vacíos, el backend aplica sus valores
        por defecto.
      </p>

      {error !== null ? <ApiErrorNotice error={error} /> : null}

      <Button type="submit" disabled={submitting}>
        {submitting ? 'Enviando pedido...' : 'Confirmar pedido'}
      </Button>
    </form>
  );
}

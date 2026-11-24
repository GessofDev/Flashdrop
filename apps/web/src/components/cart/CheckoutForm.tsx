import { useState } from 'react';
import type { FormEvent } from 'react';

import type { ApiError } from '@/api';
import { PAYMENT_METHODS } from '@/api';
import type { DeliveryQuote } from '@/api/types';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button } from '@/components/ui';
import type { Coordinates } from '@/lib/geolocation';
import { formatClp } from '@/lib/format';

import { useDeliveryLocation } from './useDeliveryLocation';

export interface CheckoutValues {
  /** Delivery address; the backend rejects an empty one. */
  address: string;
  /** Payment method the customer declares. */
  paymentMethod: string;
  /** Estimated distance in kilometres; empty when the customer leaves it out. */
  distanceKm: string;
  /** Estimated minutes of the dispatch; empty when the customer leaves it out. */
  estimatedMinutes: string;
  /**
   * Coordinates of the destination, when the customer resolved them.
   *
   * With coordinates the backend calculates the distance and validates the
   * coverage of the local; without them it rejects the order.
   */
  coordinates: Coordinates | null;
}

export interface CheckoutFormProps {
  /** Send the declared data of the checkout. */
  onSubmit: (values: CheckoutValues) => void | Promise<void>;
  /** Whether the order is being created. */
  submitting: boolean;
  /** Failure of the last attempt, with the message of the backend. */
  error: ApiError | null;
  /**
   * Report the coordinates the customer resolved, or null when there are
   * none.
   *
   * The panel owns the items of the cart, so it is the one that asks for
   * the quote with them.
   */
  onCoordinatesChange: (coordinates: Coordinates | null) => void;
  /** Quote of the backend, when the destination has coordinates. */
  quote: DeliveryQuote | null;
  /** Literal message of the backend when the quote fails. */
  quoteError: string | null;
}

const INPUT_CLASSES =
  'h-10 w-full rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted';

/**
 * Form of the checkout.
 *
 * The checkout is declarative: the customer declares the address and the
 * payment method, and the app sends them to `POST /api/orders`. There is no
 * payment gateway behind it, so the form never pretends to charge anything.
 *
 * The destination needs coordinates, because the backend calculates the
 * distance and validates the coverage of the local with them. They come from
 * the browser or, when the customer does not share its location, from the
 * geocoding of the address typed.
 */
export function CheckoutForm({
  onSubmit,
  submitting,
  error,
  onCoordinatesChange,
  quote,
  quoteError,
}: CheckoutFormProps) {
  const [address, setAddress] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<string>(PAYMENT_METHODS[0]);
  const [distanceKm, setDistanceKm] = useState('');
  const [estimatedMinutes, setEstimatedMinutes] = useState('');
  const location = useDeliveryLocation(address, onCoordinatesChange);

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();

    onSubmit({
      address,
      paymentMethod,
      distanceKm,
      estimatedMinutes,
      coordinates: location.coordinates,
    });
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
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            size="sm"
            disabled={location.locating}
            onClick={() => {
              void location.useBrowserLocation();
            }}
          >
            {location.locating ? 'Buscando tu ubicación...' : 'Usar mi ubicación'}
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={location.searching}
            onClick={() => {
              void location.searchAddress();
            }}
          >
            {location.searching ? 'Buscando la dirección...' : 'Buscar dirección'}
          </Button>
        </div>
        {location.message !== null ? (
          location.failed ? (
            <p role="alert" className="text-xs font-semibold text-destructive">
              {location.message}
            </p>
          ) : (
            <p role="status" className="text-xs text-foreground-secondary">
              {location.message}
            </p>
          )
        ) : null}
        {location.addressChangedMessage !== null ? (
          <p role="status" className="text-xs font-semibold text-foreground-secondary">
            {location.addressChangedMessage}
          </p>
        ) : null}
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
            disabled={location.coordinates !== null}
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
      {location.coordinates !== null ? (
        <p className="text-xs text-foreground-muted">
          La distancia la calcula el backend con tu ubicación, así que no la declares.
        </p>
      ) : (
        <p className="text-xs text-foreground-muted">
          La distancia y el tiempo son opcionales: si los dejas vacíos, el backend aplica sus
          valores por defecto.
        </p>
      )}

      {quote !== null ? (
        <section
          aria-label="Cotización del envío"
          className="flex flex-col gap-2 rounded-control border border-border bg-surface-hover px-3 py-2"
        >
          <p className="text-sm font-semibold text-foreground">Cotización del envío</p>
          <dl className="flex flex-col gap-1 text-sm">
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-foreground-secondary">Subtotal</dt>
              <dd className="nums-tabular font-semibold text-foreground">
                {formatClp(quote.subtotal)}
              </dd>
            </div>
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-foreground-secondary">Envío</dt>
              <dd className="nums-tabular font-semibold text-foreground">
                {formatClp(quote.deliveryFee)}
              </dd>
            </div>
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-foreground-secondary">Total</dt>
              <dd className="nums-tabular font-display font-bold text-foreground">
                {formatClp(quote.total)}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      {quoteError !== null ? (
        <div role="alert" className="rounded-control border border-border bg-surface px-3 py-2">
          <p className="text-sm text-destructive">{quoteError}</p>
        </div>
      ) : null}

      {error !== null ? <ApiErrorNotice error={error} /> : null}

      <Button type="submit" disabled={submitting}>
        {submitting ? 'Enviando pedido...' : 'Confirmar pedido'}
      </Button>
    </form>
  );
}

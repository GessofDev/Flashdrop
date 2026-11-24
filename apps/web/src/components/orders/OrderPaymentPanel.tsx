import { useCallback, useEffect, useState } from 'react';

import { ApiError, asApiError, ordersApi } from '@/api';
import type { OpenOrderPayment, OrderPayment } from '@/api/types';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Badge, Button, Skeleton } from '@/components/ui';
import type { BadgeVariant } from '@/components/ui';
import { formatOrderDate } from '@/lib/format';

/**
 * Payment of an order (spec S6).
 *
 * The customer who owns an order has to see how its payment goes and has to
 * be able to open a new attempt when the previous one was rejected. Both
 * endpoints already exist in `orders-service`: the panel asks the backend
 * and shows what the backend answers.
 */

/**
 * Label of each state of the payment, in Spanish.
 *
 * The keys are the **names** of the `PaymentStatus` of the backend
 * (`PENDIENTE`, `APROBADO`, `RECHAZADO`, `EXPIRADO`), because that is what
 * the endpoint sends. A name the app does not know is shown as it comes: the
 * panel never invents a state of the payment.
 */
const PAYMENT_STATUS_LABELS: Readonly<Record<string, string>> = {
  PENDIENTE: 'Pago pendiente',
  APROBADO: 'Pago aprobado',
  RECHAZADO: 'Pago rechazado',
  EXPIRADO: 'Pago expirado',
};

/** Tone of the chip for each state of the payment. */
const PAYMENT_STATUS_TONES: Readonly<Record<string, BadgeVariant>> = {
  PENDIENTE: 'warning',
  APROBADO: 'success',
  RECHAZADO: 'destructive',
  EXPIRADO: 'info',
};

/** Label of the order while it waits for its payment. */
const PENDING_PAYMENT_ORDER_STATUS = 'pendiente de pago';

/** State of the panel. */
interface PaymentPanelState {
  /** Payment the backend answered with. */
  payment: OrderPayment | null;
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the call is still in flight. */
  loading: boolean;
  /** Attempt opened by the retry, when the customer asked for one. */
  opened: OpenOrderPayment | null;
  /** Failure of the retry, with the message of the backend. */
  retryError: ApiError | null;
  /** Whether the retry is still in flight. */
  retrying: boolean;
}

const INITIAL_STATE: PaymentPanelState = {
  payment: null,
  error: null,
  loading: true,
  opened: null,
  retryError: null,
  retrying: false,
};

/**
 * Label of the state of the payment.
 *
 * An unknown name is shown as it comes, so the reader sees what the backend
 * sent instead of a state the app made up.
 */
function paymentStatusLabel(status: string | null): string | null {
  if (typeof status !== 'string') {
    return null;
  }

  const name = status.trim().toUpperCase();

  return PAYMENT_STATUS_LABELS[name] ?? status.trim();
}

/** Tone of the chip of the state of the payment. */
function paymentStatusTone(status: string | null): BadgeVariant {
  if (typeof status !== 'string') {
    return 'info';
  }

  return PAYMENT_STATUS_TONES[status.trim().toUpperCase()] ?? 'info';
}

/** Whether the order is still waiting for its payment. */
function isPendingPaymentOrder(orderStatus: string): boolean {
  return orderStatus.trim().toLowerCase() === PENDING_PAYMENT_ORDER_STATUS;
}

export interface OrderPaymentPanelProps {
  /** Id of the order whose payment is shown. */
  orderId: string;
}

interface PaymentDetailsProps {
  /** Name of the state of the payment, as the backend sends it. */
  statusName: string | null;
  /** Id of the attempt of payment. */
  paymentId: number | null;
  /** Deadline of the payment window, as an ISO-8601 offset date time. */
  expiresAt: string | null;
}

/**
 * State of the payment, attempt and deadline, as the backend reports them.
 *
 * A row without data is not drawn: the panel shows what the backend sent and
 * nothing else.
 */
function PaymentDetails({ statusName, paymentId, expiresAt }: PaymentDetailsProps) {
  return (
    <dl className="flex flex-col gap-2 text-sm">
      <div className="flex items-center justify-between gap-3">
        <dt className="text-foreground-secondary">Estado del pago</dt>
        <dd>
          <Badge variant={paymentStatusTone(statusName)}>
            {paymentStatusLabel(statusName) ?? 'Sin pagos registrados'}
          </Badge>
        </dd>
      </div>

      {paymentId !== null ? (
        <div className="flex items-center justify-between gap-3">
          <dt className="text-foreground-secondary">Intento de pago</dt>
          <dd className="nums-tabular font-medium text-foreground">{paymentId}</dd>
        </div>
      ) : null}

      {expiresAt !== null ? (
        <div className="flex items-center justify-between gap-3">
          <dt className="text-foreground-secondary">Plazo de pago</dt>
          <dd className="nums-tabular font-medium text-foreground">
            {formatOrderDate(expiresAt)}
          </dd>
        </div>
      ) : null}
    </dl>
  );
}

/**
 * Block of the payment of an order.
 *
 * It loads `GET /api/orders/{id}/payments` when the screen opens and shows
 * the state of the payment, the deadline of the payment window and the
 * attempt the backend reports.
 *
 * The retry is offered only when it makes sense: the order is
 * `Pendiente de pago` and there is no payment still open. It calls
 * `POST /api/orders/{id}/payments` and shows the attempt the backend opens.
 *
 * The `paymentUrl` of the backend is never rendered: it is a fake scheme and
 * there is no payment gateway behind it, so the app shows the state and the
 * deadline, never a link to pay.
 */
export function OrderPaymentPanel({ orderId }: OrderPaymentPanelProps) {
  const [state, setState] = useState<PaymentPanelState>(INITIAL_STATE);

  useEffect(() => {
    let active = true;

    setState(INITIAL_STATE);

    if (orderId.length === 0) {
      setState({
        ...INITIAL_STATE,
        error: new ApiError('Pedido no encontrado', { status: 404 }),
        loading: false,
      });

      return () => {
        active = false;
      };
    }

    ordersApi
      .getOrderPayment(orderId)
      .then((payment) => {
        if (active) {
          setState({ ...INITIAL_STATE, payment, loading: false });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setState({ ...INITIAL_STATE, error: asApiError(cause), loading: false });
        }
      });

    return () => {
      active = false;
    };
  }, [orderId]);

  const retry = useCallback(() => {
    setState((previous) => ({ ...previous, retrying: true, retryError: null }));

    ordersApi
      .openOrderPayment(orderId)
      .then((opened) => {
        setState((previous) => ({ ...previous, retrying: false, opened }));
      })
      .catch((cause: unknown) => {
        setState((previous) => ({
          ...previous,
          retrying: false,
          retryError: asApiError(cause),
        }));
      });
  }, [orderId]);

  const payment = state.payment;
  const opened = state.opened;

  // The attempt the customer sees is the open one: the retry leaves an open
  // payment behind, so it replaces the last attempt the backend reported.
  const statusName = opened !== null ? 'PENDIENTE' : (payment?.paymentStatus ?? null);
  const paymentId = opened !== null ? opened.paymentId : (payment?.paymentId ?? null);
  const expiresAt =
    opened !== null ? opened.paymentExpiresAt : (payment?.paymentExpiresAt ?? null);
  const orderStatus = payment?.orderStatus ?? '';

  // A payment is still open while the backend reports it as `PENDIENTE`.
  const paymentOpen =
    typeof statusName === 'string' && statusName.trim().toUpperCase() === 'PENDIENTE';

  const canRetry = isPendingPaymentOrder(orderStatus) && !paymentOpen && !state.retrying;

  return (
    <section
      aria-labelledby="order-payment-title"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <h2 id="order-payment-title" className="font-display text-lg font-bold text-foreground">
        Pago del pedido
      </h2>

      {state.loading ? (
        <Skeleton label="Cargando el pago" blocks={1} height="h-24" />
      ) : state.error !== null ? (
        <ApiErrorNotice error={state.error} />
      ) : (
        <>
          <PaymentDetails
            statusName={statusName}
            paymentId={paymentId}
            expiresAt={expiresAt}
          />

          <p className="text-sm text-foreground-secondary">
            El estado y el plazo los informa el backend. La app no abre ningún enlace de pago.
          </p>

          {state.retryError !== null ? (
            <ApiErrorNotice error={state.retryError} />
          ) : null}

          {opened !== null ? (
            <p role="status" className="text-sm font-semibold text-success">
              Abrimos un nuevo intento de pago.
            </p>
          ) : null}

          {canRetry ? (
            <Button variant="primary" size="sm" className="self-start" onClick={retry}>
              Reintentar pago
            </Button>
          ) : null}
        </>
      )}
    </section>
  );
}

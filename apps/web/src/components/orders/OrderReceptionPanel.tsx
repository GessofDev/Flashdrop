import type { OrderDetail } from '@/api';
import { Badge } from '@/components/ui';
import type { BadgeVariant } from '@/components/ui';
import { formatOrderDate } from '@/lib/format';

/**
 * Reception and cancellation of an order (spec S8).
 *
 * The backend already sends the reception mode of the order, the code the
 * customer dictates to the courier and the reason of an automatic
 * cancellation, both in the detail and in the listing.
 *
 * The panel shows exactly what the answer carries: a field the backend does
 * not send is not shown, and the app never writes a code, a reason or a
 * date of its own.
 */

/**
 * Label of each reception mode of the backend, in Spanish.
 *
 * The keys are the **names** of the `ReceptionMode` of the backend
 * (`NORMAL`, `SEGURO`), because that is what the endpoint sends. A name the
 * app does not know is shown as it comes: the app never invents a mode.
 */
const RECEPTION_MODE_LABELS: Readonly<Record<string, string>> = {
  NORMAL: 'Entrega normal',
  SEGURO: 'Entrega segura',
};

/** Tone of the chip of each reception mode. */
const RECEPTION_MODE_TONES: Readonly<Record<string, BadgeVariant>> = {
  NORMAL: 'info',
  SEGURO: 'success',
};

/**
 * Text the backend sent, or `null` when it sent nothing.
 *
 * An empty string is not a value: the backend leaves the field out when it
 * has nothing to say about it.
 */
function readText(value: string | null | undefined): string | null {
  if (typeof value !== 'string') {
    return null;
  }

  const trimmed = value.trim();

  return trimmed.length === 0 ? null : trimmed;
}

/** Fields of the reception of an order, in the detail and in the listing. */
export interface OrderReceptionFields {
  /** Reception mode of the order: `NORMAL` or `SEGURO`. */
  receptionMode?: string | null;
  /** Verification code of a secure order. */
  deliveryCode?: string | null;
  /** Reason code of an automatic cancellation. */
  cancelReason?: string | null;
  /** Text of the cancellation, ready to show. */
  cancelReasonMessage?: string | null;
  /** Instant of the cancellation. */
  cancelledAt?: string | null;
}

/** Reception of an order, read from the answer of the backend. */
export interface OrderReception {
  /** Label of the reception mode, in Spanish. */
  modeLabel: string | null;
  /** Tone of the chip of the reception mode. */
  modeTone: BadgeVariant;
  /** Code the customer dictates to the courier, when the backend sends one. */
  deliveryCode: string | null;
  /** Reason code of the cancellation, when the backend sends one. */
  cancelReason: string | null;
  /** Text of the cancellation, ready to show. */
  cancelReasonMessage: string | null;
  /** Instant of the cancellation, when the backend sends one. */
  cancelledAt: string | null;
}

/**
 * Reception of an order, read from what the backend sent.
 *
 * Nothing is invented here: a field the answer does not carry stays `null`
 * and the caller shows nothing for it.
 */
export function orderReception(order: OrderReceptionFields): OrderReception {
  const mode = readText(order.receptionMode);

  return {
    modeLabel: mode === null ? null : (RECEPTION_MODE_LABELS[mode.toUpperCase()] ?? mode),
    modeTone: mode === null ? 'info' : (RECEPTION_MODE_TONES[mode.toUpperCase()] ?? 'info'),
    deliveryCode: readText(order.deliveryCode),
    cancelReason: readText(order.cancelReason),
    cancelReasonMessage: readText(order.cancelReasonMessage),
    cancelledAt: readText(order.cancelledAt),
  };
}

export interface OrderReceptionPanelProps {
  /** Order the backend answered with. */
  order: OrderDetail;
}

/**
 * Block of the reception of an order.
 *
 * It shows the reception mode the backend sent, the verification code of a
 * secure order — only when the answer carries one, because the backend
 * sends it to the owner of the order, in the secure mode and while the order
 * is not delivered — and the reason of a cancellation with its date.
 */
export function OrderReceptionPanel({ order }: OrderReceptionPanelProps) {
  const reception = orderReception(order);

  const cancelled =
    reception.cancelReason !== null ||
    reception.cancelReasonMessage !== null ||
    reception.cancelledAt !== null;

  if (reception.modeLabel === null && reception.deliveryCode === null && !cancelled) {
    return null;
  }

  return (
    <section
      aria-labelledby="order-reception-title"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <h2 id="order-reception-title" className="font-display text-lg font-bold text-foreground">
        Recepción del pedido
      </h2>

      {reception.modeLabel !== null ? (
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex items-center justify-between gap-3">
            <dt className="text-foreground-secondary">Modo de recepción</dt>
            <dd>
              <Badge variant={reception.modeTone}>{reception.modeLabel}</Badge>
            </dd>
          </div>
        </dl>
      ) : null}

      {reception.deliveryCode !== null ? (
        <div className="flex flex-col gap-1 rounded-control bg-canvas p-4">
          <span className="label-code text-xs text-foreground-muted">Código de verificación</span>
          <p className="nums-tabular font-display text-4xl font-bold tracking-[0.3em] text-foreground">
            {reception.deliveryCode}
          </p>
          <p className="text-sm text-foreground-secondary">
            Este es el código que dictas al repartidor para confirmar la entrega.
          </p>
        </div>
      ) : null}

      {cancelled ? (
        <div className="flex flex-col gap-1 rounded-control bg-destructive-surface p-4">
          <span className="label-code text-xs text-destructive">Pedido cancelado</span>
          {reception.cancelReasonMessage !== null ? (
            <p className="text-sm font-medium text-destructive">{reception.cancelReasonMessage}</p>
          ) : null}
          {reception.cancelReason !== null ? (
            <p className="text-xs text-destructive">
              Código del motivo: {reception.cancelReason}
            </p>
          ) : null}
          {reception.cancelledAt !== null ? (
            <p className="nums-tabular text-xs text-destructive">
              Cancelado el {formatOrderDate(reception.cancelledAt)}
            </p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

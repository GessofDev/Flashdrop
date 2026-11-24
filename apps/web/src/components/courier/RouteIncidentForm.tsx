import { useState } from 'react';
import type { FormEvent } from 'react';

import type { DeliveryRoute } from '@/api/delivery';

export type RouteIncidentMode = 'release' | 'cancel-after-pickup';

export interface RouteIncidentFormProps {
  /** Route being released or cancelled after pickup. */
  route: DeliveryRoute;
  /** Whether the courier is releasing pre-pickup or cancelling post-pickup. */
  mode: RouteIncidentMode;
  /** Whether the request is currently in flight. */
  pending: boolean;
  /** Submit the release or cancellation request. */
  onSubmit: (payload: {
    mode: RouteIncidentMode;
    status: 'Cancelado' | 'Rechazado';
    reason: string;
  }) => Promise<void>;
  /** Dismiss the form without calling the backend. */
  onCancel: () => void;
}

/**
 * Inline form for releasing an order before pickup or cancelling/rejecting
 * an order after pickup (T9).
 */
export function RouteIncidentForm({
  route,
  mode,
  pending,
  onSubmit,
  onCancel,
}: RouteIncidentFormProps) {
  const [status, setStatus] = useState<'Cancelado' | 'Rechazado'>('Cancelado');
  const [reason, setReason] = useState('');

  const isRelease = mode === 'release';
  const orderLabel = route.code !== null ? `#${route.code}` : `Pedido ${route.orderId}`;

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (reason.trim() === '') {
      return;
    }

    void onSubmit({ mode, status, reason: reason.trim() });
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <div className="flex flex-col gap-1">
        <span className="label-code text-xs text-primary">
          {isRelease ? 'Liberar asignación' : 'Anulación tras retiro'}
        </span>
        <h3 className="font-display text-lg font-bold text-foreground">
          {isRelease ? `Liberar pedido ${orderLabel}` : `Anular pedido ${orderLabel}`}
        </h3>
      </div>

      {!isRelease ? (
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium text-foreground">Estado final</span>
          <select
            aria-label="Estado final"
            value={status}
            onChange={(event) => setStatus(event.target.value as 'Cancelado' | 'Rechazado')}
            disabled={pending}
            className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground focus:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
          >
            <option value="Cancelado">Cancelado</option>
            <option value="Rechazado">Rechazado</option>
          </select>
        </label>
      ) : null}

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium text-foreground">
          {isRelease ? 'Motivo de liberación' : 'Motivo de anulación'}
        </span>
        <textarea
          aria-label={isRelease ? 'Motivo de liberación' : 'Motivo de anulación'}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          disabled={pending}
          maxLength={500}
          rows={3}
          required
          placeholder="Describe brevemente el motivo..."
          className="rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground placeholder:text-foreground-muted focus:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
        />
      </label>

      <div className="flex flex-wrap justify-end gap-2">
        <button
          type="button"
          disabled={pending}
          onClick={onCancel}
          className="inline-flex h-10 items-center justify-center rounded-control border border-border bg-surface-hover px-4 text-sm font-semibold text-foreground transition-colors hover:bg-border/40 disabled:opacity-45"
        >
          Cancelar
        </button>
        <button
          type="submit"
          disabled={pending || reason.trim() === ''}
          className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white transition-colors hover:bg-primary-hover disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-45"
        >
          {isRelease ? 'Confirmar liberación' : 'Confirmar anulación'}
        </button>
      </div>
    </form>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { asApiError, ordersApi } from '@/api';
import type { ApiError, CreatedOrderResult } from '@/api';
import { routePaths } from '@/app/routePaths';
import { formatClp } from '@/lib/format';

export interface OrderConfirmationProps {
  /** Order the backend created. */
  order: CreatedOrderResult;
}

/** State of the code of the order, read from the detail of the backend. */
type CodeState =
  | { status: 'loading' }
  | { status: 'ready'; code: string }
  | { status: 'failed'; error: ApiError };

/**
 * Confirmation of the created order.
 *
 * The total it shows is the one the backend computed, not the sum the client
 * made: the price of the dispatch and any other correction of the backend
 * live only there.
 *
 * The code of the order is read from `GET /api/orders/{id}`. The create
 * endpoint answers with the id and the total only, and the format of the code
 * belongs to the backend: deriving it in the client would duplicate a rule
 * that can change. When the detail cannot be read, the confirmation shows the
 * id of the order and the literal message of the backend instead of a code
 * nobody can verify.
 */
export function OrderConfirmation({ order }: OrderConfirmationProps) {
  const [code, setCode] = useState<CodeState>({ status: 'loading' });

  useEffect(() => {
    let active = true;

    void ordersApi
      .getOrder(order.id)
      .then((detail) => {
        if (active) {
          setCode({ status: 'ready', code: detail.code });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setCode({ status: 'failed', error: asApiError(cause) });
        }
      });

    return () => {
      active = false;
    };
  }, [order.id]);

  return (
    <div
      role="status"
      className="flex flex-col gap-3 rounded-container border border-success bg-success-surface p-4"
    >
      <h3 className="font-display text-base font-bold text-foreground">Pedido creado</h3>

      <dl className="flex flex-col gap-2 text-sm">
        <div className="flex items-center justify-between gap-3">
          <dt className="text-foreground-secondary">Código del pedido</dt>
          <dd className="nums-tabular label-code font-bold text-foreground">
            {code.status === 'ready' ? `#${code.code}` : '—'}
          </dd>
        </div>
        <div className="flex items-baseline justify-between gap-3">
          <dt className="text-foreground-secondary">Total del pedido</dt>
          <dd className="nums-tabular font-display text-lg font-bold text-foreground">
            {formatClp(order.total)}
          </dd>
        </div>
      </dl>

      {code.status === 'failed' ? (
        <div className="flex flex-col gap-1 rounded-control border border-destructive bg-destructive-surface px-3 py-2">
          <p className="text-xs font-semibold text-destructive">
            No pudimos leer el código del pedido.
          </p>
          <p className="nums-tabular text-xs text-foreground-secondary">
            Identificador: {order.id}
          </p>
          <p className="text-xs text-foreground-secondary">{code.error.message}</p>
        </div>
      ) : null}

      <Link
        to={routePaths.orders}
        className="text-sm font-semibold text-primary transition-colors hover:text-primary-hover"
      >
        Ver mis pedidos
      </Link>
    </div>
  );
}

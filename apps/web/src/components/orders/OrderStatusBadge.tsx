import { Badge } from '@/components/ui';
import type { BadgeVariant } from '@/components/ui';

/**
 * Tone of the chip for each status of the backend.
 *
 * The tone is a reading of the milestone, not a rule of the backend: the
 * label shown is always the one the backend sent.
 */
const TONE_BY_STATUS: Readonly<Record<string, BadgeVariant>> = {
  'nuevo pedido': 'info',
  preparando: 'warning',
  'listo para retiro': 'warning',
  retirado: 'info',
  'en camino': 'info',
  entregado: 'success',
};

export interface OrderStatusBadgeProps {
  /** Status of the order, as the backend labels it. */
  status: string;
}

/**
 * Chip with the status of the order.
 *
 * The label is the one of the backend — `Nuevo pedido`, `Preparando`,
 * `Listo para retiro`, `Retirado`, `En camino` or `Entregado` — because the
 * status is the vocabulary of the service and the app does not rewrite it.
 */
export function OrderStatusBadge({ status }: OrderStatusBadgeProps) {
  const tone = TONE_BY_STATUS[status.trim().toLowerCase()] ?? 'info';

  return <Badge variant={tone}>{status}</Badge>;
}

import { elapsedMilliseconds, formatElapsed } from '@/lib/elapsed';
import { MISSING_VALUE } from '@/lib/format';

export interface ElapsedTimerProps {
  /** Instant the order was created, as an ISO-8601 offset date time. */
  createdAt: string;
  /** Current instant, in epoch milliseconds. */
  now: number;
}

/**
 * The time the order has been waiting, measured from its creation (S12).
 *
 * The measure is frontend-only: the backend sends the instant of the
 * creation and nothing else, so the board counts the minutes from that
 * instant. An instant that is not a date has no measure and the board says
 * so instead of showing a number it cannot read.
 */
export function ElapsedTimer({ createdAt, now }: ElapsedTimerProps) {
  const elapsed = elapsedMilliseconds(createdAt, now);

  if (elapsed === null) {
    return <span className="nums-tabular text-sm text-foreground-muted">{MISSING_VALUE}</span>;
  }

  return (
    <span className="nums-tabular font-display text-sm font-bold text-foreground">
      {formatElapsed(elapsed)}
    </span>
  );
}

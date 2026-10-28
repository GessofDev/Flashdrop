/**
 * The elapsed time of an order, measured in the browser (spec S12).
 *
 * The backend sends the instant of the creation of the order and nothing
 * else: the board measures the time from that instant. The measure is
 * frontend-only and it never invents a duration the backend did not send — an
 * instant that is not a date has no elapsed time and the board says so.
 */

/**
 * Milliseconds between the creation of the order and an instant.
 *
 * `null` when the backend did not send a readable instant.
 */
export function elapsedMilliseconds(createdAt: string, now: number): number | null {
  const trimmed = createdAt.trim();

  if (trimmed.length === 0) {
    return null;
  }

  const instant = new Date(trimmed);

  if (Number.isNaN(instant.getTime())) {
    return null;
  }

  return Math.max(0, now - instant.getTime());
}

/**
 * The elapsed time, as the board writes it.
 *
 * It counts minutes and seconds under the hour (`12:34`) and adds the hours
 * from the hour on (`1:02:03`).
 */
export function formatElapsed(milliseconds: number): string {
  const total = Math.max(0, Math.floor(milliseconds / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;

  if (hours > 0) {
    return `${hours}:${pad(minutes)}:${pad(seconds)}`;
  }

  return `${minutes}:${pad(seconds)}`;
}

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}

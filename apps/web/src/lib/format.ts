/**
 * Amount of money, formatted the way the app shows it.
 *
 * The Chilean peso has no decimals in the interface, so the amount is
 * rounded and grouped with a dot, the Chilean way: `$12.970`.
 */
export function formatClp(amount: number): string {
  const rounded = Math.round(amount);
  const sign = rounded < 0 ? '-' : '';
  const digits = Math.abs(rounded).toString();
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

  return `${sign}$${grouped}`;
}

/**
 * Placeholder of a value the backend did not send.
 *
 * The screens show it instead of an empty cell, so the reader knows the data
 * is missing and not that the app forgot it.
 */
export const MISSING_VALUE = '—';

/**
 * Instant of the backend, in the Chilean time zone.
 *
 * `orders-service` answers with an ISO-8601 offset date time. The interface
 * shows the hour of Chile, so the format is fixed to `America/Santiago` and
 * the text does not depend on the machine that renders it.
 *
 * A value that is not a date is shown as it arrives: the client never hides
 * what the backend sent.
 */
export function formatOrderDate(value: string): string {
  const trimmed = value.trim();

  if (trimmed.length === 0) {
    return MISSING_VALUE;
  }

  const instant = new Date(trimmed);

  if (Number.isNaN(instant.getTime())) {
    return trimmed;
  }

  return new Intl.DateTimeFormat('es-CL', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'America/Santiago',
  }).format(instant);
}

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

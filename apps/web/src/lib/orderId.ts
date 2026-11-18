/**
 * Numeric id of an order, in the space the other services use.
 *
 * `orders-service` owns the orders with a UUID and publishes them with the
 * numeric id every other service uses: the numeric id is the least
 * significant half of the UUID (`IdConverter.toUuid` builds
 * `new UUID(0, id)` and `IdConverter.toLong` reads it back), so both spaces
 * name the same order.
 *
 * `reviews-service` works with the numeric space, so the web derives it from
 * the id of the order it already holds. The reader answers `null` when the
 * id is not a UUID of that shape — or a number bigger than the safe integer
 * range — so the screen says it cannot ask for the ratings instead of
 * sending a wrong id.
 */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-([0-9a-f]{12})$/i;

/** Mask of the least significant half of a UUID. */
const LEAST_SIGNIFICANT_MASK = 0x7fffffffffffffffn;

/**
 * Numeric id of an order.
 *
 * It accepts the UUID the orders screens hold and a plain number, so the
 * screen keeps working if the backend starts publishing the numeric id
 * directly.
 */
export function numericOrderId(orderId: string): number | null {
  const trimmed = orderId.trim();

  if (trimmed.length === 0) {
    return null;
  }

  if (/^\d+$/.test(trimmed)) {
    const value = Number(trimmed);

    return Number.isSafeInteger(value) ? value : null;
  }

  const match = UUID_PATTERN.exec(trimmed);

  if (match === null) {
    return null;
  }

  const leastSignificant = match[1] ?? '';

  if (leastSignificant.length !== 12) {
    return null;
  }

  const value = BigInt(`0x${leastSignificant}`) & LEAST_SIGNIFICANT_MASK;

  if (value > BigInt(Number.MAX_SAFE_INTEGER)) {
    return null;
  }

  return Number(value);
}

/**
 * Helpers of the Chilean RUT.
 *
 * The account identifier accepts the RUT with or without dots and hyphen;
 * the value that leaves the input is always the canonical form
 * `16.482.915-K`.
 */

/** Strip everything that is not a digit or the verifier, and group it. */
export function formatRut(value: string): string {
  const clean = value.replace(/[^0-9kK]/g, '').toUpperCase().slice(0, 9);

  if (clean.length === 0) {
    return '';
  }

  const hasVerifier = clean.endsWith('K');
  const body = hasVerifier ? clean.slice(0, -1) : clean;
  const grouped = body.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

  return hasVerifier ? `${grouped}-K` : grouped;
}

/**
 * Check the verifier of a RUT with the module 11 algorithm.
 *
 * It is the same check the mockup announces next to the field; the backend
 * stays the authority of the identifier.
 */
export function isValidRut(value: string): boolean {
  const clean = value.replace(/[^0-9kK]/g, '').toUpperCase();

  if (!/^\d+[0-9K]$/.test(clean)) {
    return false;
  }

  const body = clean.slice(0, -1);
  const verifier = clean.slice(-1);

  let sum = 0;
  let factor = 2;

  for (let index = body.length - 1; index >= 0; index -= 1) {
    sum += factor * Number(body.charAt(index));
    factor = factor < 7 ? factor + 1 : 2;
  }

  const remainder = 11 - (sum % 11);
  const expected = remainder === 11 ? '0' : remainder === 10 ? 'K' : String(remainder);

  return verifier === expected;
}

import { apiBaseUrl } from './baseUrl';

/** URL that already points to a host: `http:`, `https:`, `data:`, `blob:` or `//host`. */
const ABSOLUTE_URL = /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i;

/**
 * Resolve the URL of a picture of the catalog.
 *
 * The catalog answers with a relative path (`/catalog/images/products/...`),
 * because the bytes live in the private storage of the service and are served
 * through the gateway. The app prepends its configured backend base URL, so
 * the picture is requested from the API and not from the static host of the
 * web app.
 *
 * An absolute URL is used as it is: the app never rewrites a host it did not
 * choose.
 *
 * @param image Value of the `image` field of the catalog, or `null`.
 * @return The URL to request, or `null` when there is no picture.
 */
export function resolveImageUrl(image: string | null | undefined): string | null {
  if (typeof image !== 'string') {
    return null;
  }

  const trimmed = image.trim();

  if (trimmed.length === 0) {
    return null;
  }

  if (ABSOLUTE_URL.test(trimmed)) {
    return trimmed;
  }

  const base = apiBaseUrl();

  if (base.length === 0) {
    return trimmed;
  }

  return `${base}/${trimmed.replace(/^\/+/, '')}`;
}

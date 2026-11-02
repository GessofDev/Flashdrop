/**
 * Base URL of the backend.
 *
 * It is empty by default, so the browser calls the paths the dev server
 * proxies (`/api`, `/auth` and `/catalog`) on its own origin. `VITE_API_BASE_URL`
 * overrides it when the web app is published on a different host than the API.
 */
export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;

  return typeof configured === 'string' ? configured.replace(/\/+$/, '') : '';
}

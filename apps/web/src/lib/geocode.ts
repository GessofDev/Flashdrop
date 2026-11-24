import type { Coordinates } from './geolocation';

/**
 * Geocoding of the address the customer typed.
 *
 * The provider is Nominatim (OpenStreetMap): it needs no API key and no new
 * dependency. Its usage policy asks for an application that identifies
 * itself and for a scarce use — roughly one request per second — and a
 * browser cannot set the `User-Agent` of a fetch, so this module keeps the
 * requests to one per click and the provider can be swapped behind it.
 *
 * The module is the only place that knows the provider: the rest of the app
 * asks for coordinates and reads the failure it raises.
 */

/** Endpoint of the geocoder. */
const NOMINATIM_ENDPOINT = 'https://nominatim.openstreetmap.org/search';

/** Deadline of the geocoder, in milliseconds. */
const GEOCODE_TIMEOUT_MS = 10_000;

/** One point the geocoder found for the address. */
interface GeocodePoint {
  /** Latitude of the point, as a string. */
  lat: string;
  /** Longitude of the point, as a string. */
  lon: string;
}

/**
 * Failure of the geocoder, with the message the form shows.
 *
 * The customer keeps the form usable: the address typed is never lost.
 */
export class GeocodeError extends Error {
  constructor(message: string) {
    super(message);

    this.name = 'GeocodeError';
  }
}

/** Message shown when the geocoder does not find the address. */
export const ADDRESS_NOT_FOUND_MESSAGE =
  'No encontramos esa dirección. Prueba con más detalles.';

/** Message shown when the geocoder cannot be reached. */
export const GEOCODER_UNAVAILABLE_MESSAGE =
  'No se pudo consultar el servicio de direcciones. Inténtalo de nuevo.';

/** Read the first point of the geocoder, or null when there is none. */
function firstPoint(body: unknown): GeocodePoint | null {
  if (!Array.isArray(body)) {
    return null;
  }

  const [first] = body;

  if (typeof first !== 'object' || first === null) {
    return null;
  }

  const { lat, lon } = first as Partial<GeocodePoint>;

  if (typeof lat !== 'string' || typeof lon !== 'string') {
    return null;
  }

  return { lat, lon };
}

/**
 * Resolve the address the customer typed into coordinates.
 *
 * It raises a {@link GeocodeError} when the geocoder does not answer or does
 * not find the address: the form shows the message and stays usable.
 */
export async function geocodeAddress(address: string): Promise<Coordinates> {
  const query = address.trim();

  if (query.length === 0) {
    throw new GeocodeError('Escribe la dirección para buscar su ubicación.');
  }

  const url = `${NOMINATIM_ENDPOINT}?format=jsonv2&limit=1&q=${encodeURIComponent(query)}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), GEOCODE_TIMEOUT_MS);

  let response: Response;

  try {
    response = await fetch(url, { signal: controller.signal });
  } catch {
    throw new GeocodeError(GEOCODER_UNAVAILABLE_MESSAGE);
  } finally {
    clearTimeout(timer);
  }

  if (!response.ok) {
    throw new GeocodeError(GEOCODER_UNAVAILABLE_MESSAGE);
  }

  let body: unknown;

  try {
    body = await response.json();
  } catch {
    throw new GeocodeError(GEOCODER_UNAVAILABLE_MESSAGE);
  }

  const point = firstPoint(body);

  if (point === null) {
    throw new GeocodeError(ADDRESS_NOT_FOUND_MESSAGE);
  }

  const latitude = Number.parseFloat(point.lat);
  const longitude = Number.parseFloat(point.lon);

  if (Number.isNaN(latitude) || Number.isNaN(longitude)) {
    throw new GeocodeError(GEOCODER_UNAVAILABLE_MESSAGE);
  }

  return { latitude, longitude };
}

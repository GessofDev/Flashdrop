/**
 * Location of the destination, as the browser reports it.
 *
 * The coordinates are the ones the backend needs to calculate the distance
 * and to validate the coverage of the local, so they always travel together:
 * the backend rejects one without the other.
 */
export interface Coordinates {
  /** Latitude of the destination, in degrees. */
  latitude: number;
  /** Longitude of the destination, in degrees. */
  longitude: number;
}

/** Message shown when the browser does not offer the geolocation API. */
export const LOCATION_UNAVAILABLE_MESSAGE =
  'Tu navegador no permite obtener tu ubicación.';

/** Message shown when the customer does not grant the permission. */
export const LOCATION_DENIED_MESSAGE =
  'No compartiste tu ubicación. Puedes buscar la dirección escrita.';

/** Message shown when the browser takes too long to answer. */
export const LOCATION_TIMEOUT_MESSAGE =
  'Tu ubicación tardó demasiado. Puedes buscar la dirección escrita.';

/** Deadline of the browser geolocation, in milliseconds. */
const LOCATION_TIMEOUT_MS = 10_000;

/**
 * Failure of the browser geolocation.
 *
 * It carries the message the form shows: the customer always keeps the
 * fallback (the geocoding of the address typed) available.
 */
export class LocationError extends Error {
  constructor(message: string) {
    super(message);

    this.name = 'LocationError';
  }
}

/** Message of the failure the browser reports with its error code. */
function messageForErrorCode(code: number | undefined): string {
  switch (code) {
    case 1:
      return LOCATION_DENIED_MESSAGE;
    case 3:
      return LOCATION_TIMEOUT_MESSAGE;
    default:
      return LOCATION_UNAVAILABLE_MESSAGE;
  }
}

/**
 * Ask the browser where the customer is.
 *
 * The permission may be denied, the API may not exist and the answer may
 * take too long: none of them breaks the checkout, because the address
 * written is the fallback.
 */
export function requestBrowserLocation(): Promise<Coordinates> {
  return new Promise<Coordinates>((resolve, reject) => {
    const geolocation =
      typeof navigator === 'undefined' ? undefined : navigator.geolocation;

    if (geolocation === undefined) {
      reject(new LocationError(LOCATION_UNAVAILABLE_MESSAGE));
      return;
    }

    geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
      },
      (cause) => {
        reject(new LocationError(messageForErrorCode(cause.code)));
      },
      { enableHighAccuracy: false, timeout: LOCATION_TIMEOUT_MS, maximumAge: 0 },
    );
  });
}

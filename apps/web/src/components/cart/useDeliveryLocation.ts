import { useState } from 'react';

import { GeocodeError, geocodeAddress } from '@/lib/geocode';
import type { Coordinates } from '@/lib/geolocation';
import { LocationError, requestBrowserLocation } from '@/lib/geolocation';

/** Message shown when the customer resolved the location of the destination. */
const LOCATION_CONFIRMED_MESSAGE =
  'Ubicación confirmada: el envío se calcula con tu ubicación.';

/**
 * Message shown when the address changed after the location was confirmed.
 *
 * The coordinates stay: they are the ones the customer confirmed. The app
 * says it out loud instead of pretending that both belong together.
 */
const ADDRESS_CHANGED_MESSAGE =
  'Cambiaste la dirección después de confirmar la ubicación: puede que no coincidan.';

/** Message shown when the browser does not answer with a position. */
const LOCATION_FAILED_MESSAGE =
  'No se pudo obtener tu ubicación. Puedes buscar la dirección escrita.';

/** Message shown when the geocoder does not answer. */
const GEOCODE_FAILED_MESSAGE = 'No se pudo buscar la dirección. Inténtalo de nuevo.';

export interface DeliveryLocationState {
  /** Coordinates the customer resolved, or null when there are none. */
  coordinates: Coordinates | null;
  /** Whether the browser is answering. */
  locating: boolean;
  /** Whether the geocoder is answering. */
  searching: boolean;
  /** Message of the last attempt, or null when there was none. */
  message: string | null;
  /** Whether the last attempt failed. */
  failed: boolean;
  /**
   * Message shown when the address changed after the location was confirmed,
   * or null when there is nothing to warn about.
   */
  addressChangedMessage: string | null;
  /** Ask the browser where the customer is. */
  useBrowserLocation: () => Promise<void>;
  /** Resolve the address the customer typed into coordinates. */
  searchAddress: () => Promise<void>;
}

/**
 * Coordinates of the destination of the checkout.
 *
 * The browser answers first and the geocoding of the address typed is the
 * fallback, so the customer always has a way to resolve the location the
 * backend needs.
 */
export function useDeliveryLocation(
  address: string,
  onCoordinatesChange: (coordinates: Coordinates | null) => void,
): DeliveryLocationState {
  const [coordinates, setCoordinates] = useState<Coordinates | null>(null);
  const [locating, setLocating] = useState(false);
  const [searching, setSearching] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [confirmedAddress, setConfirmedAddress] = useState<string | null>(null);

  /** Keep the coordinates the customer resolved and tell the panel about them. */
  const acceptCoordinates = (resolved: Coordinates): void => {
    setCoordinates(resolved);
    setMessage(LOCATION_CONFIRMED_MESSAGE);
    setFailed(false);
    setConfirmedAddress(address);
    onCoordinatesChange(resolved);
  };

  /** Ask the browser where the customer is. */
  const useBrowserLocation = async (): Promise<void> => {
    setLocating(true);
    setMessage(null);

    try {
      acceptCoordinates(await requestBrowserLocation());
    } catch (cause) {
      setMessage(cause instanceof LocationError ? cause.message : LOCATION_FAILED_MESSAGE);
      setFailed(true);
    } finally {
      setLocating(false);
    }
  };

  /** Resolve the address the customer typed into coordinates. */
  const searchAddress = async (): Promise<void> => {
    setSearching(true);
    setMessage(null);

    try {
      acceptCoordinates(await geocodeAddress(address));
    } catch (cause) {
      setMessage(cause instanceof GeocodeError ? cause.message : GEOCODE_FAILED_MESSAGE);
      setFailed(true);
    } finally {
      setSearching(false);
    }
  };

  return {
    coordinates,
    locating,
    searching,
    message,
    failed,
    addressChangedMessage:
      coordinates !== null &&
      confirmedAddress !== null &&
      address.trim() !== confirmedAddress.trim()
        ? ADDRESS_CHANGED_MESSAGE
        : null,
    useBrowserLocation,
    searchAddress,
  };
}

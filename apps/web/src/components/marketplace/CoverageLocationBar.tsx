import { useState } from 'react';

import { GeocodeError, geocodeAddress } from '@/lib/geocode';
import type { Coordinates } from '@/lib/geolocation';
import { LocationError, requestBrowserLocation } from '@/lib/geolocation';

/** Fallback message when browser geolocation fails without a typed LocationError. */
const LOCATION_FAILED_MESSAGE =
  'No se pudo obtener tu ubicación. Puedes buscar la dirección escrita.';

/** Fallback message when geocoding fails without a typed GeocodeError. */
const GEOCODE_FAILED_MESSAGE = 'No se pudo buscar la dirección. Inténtalo de nuevo.';

export interface CoverageLocationBarProps {
  /** Current destination coordinates, or null when no location is active. */
  coordinates: Coordinates | null;
  /** Whether the user chose to view all restaurants despite having coordinates. */
  showingAll: boolean;
  /** Called when the user resolves new coordinates or clears them. */
  onCoordinatesChange: (coordinates: Coordinates | null) => void;
  /** Called when the user switches between filtered coverage and all restaurants. */
  onToggleShowAll: (showAll: boolean) => void;
}

/**
 * Delivery coverage bar for the customer marketplace (spec S10, T7).
 *
 * Lets the customer share browser location or geocode an address so
 * `GET /catalog/restaurants` filters by delivery coverage, while keeping an
 * explicit toggle to view all restaurants or clear the location.
 */
export function CoverageLocationBar({
  coordinates,
  showingAll,
  onCoordinatesChange,
  onToggleShowAll,
}: CoverageLocationBarProps) {
  const [address, setAddress] = useState('');
  const [locating, setLocating] = useState(false);
  const [searching, setSearching] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const busy = locating || searching;

  const handleBrowserLocation = async (): Promise<void> => {
    setLocating(true);
    setErrorMessage(null);

    try {
      const resolved = await requestBrowserLocation();
      onCoordinatesChange(resolved);
    } catch (cause) {
      setErrorMessage(cause instanceof LocationError ? cause.message : LOCATION_FAILED_MESSAGE);
    } finally {
      setLocating(false);
    }
  };

  const handleSearchAddress = async (): Promise<void> => {
    setSearching(true);
    setErrorMessage(null);

    try {
      const resolved = await geocodeAddress(address);
      onCoordinatesChange(resolved);
    } catch (cause) {
      setErrorMessage(cause instanceof GeocodeError ? cause.message : GEOCODE_FAILED_MESSAGE);
    } finally {
      setSearching(false);
    }
  };

  const handleClear = (): void => {
    setErrorMessage(null);
    onCoordinatesChange(null);
  };

  return (
    <section
      aria-label="Cobertura en tu zona"
      className="flex flex-col gap-3 rounded-container border border-border bg-surface p-4 shadow-card"
    >
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <button
          type="button"
          onClick={() => void handleBrowserLocation()}
          disabled={busy}
          className="inline-flex shrink-0 items-center justify-center rounded-control border border-border bg-surface-hover px-3.5 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-border/40 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
        >
          {locating ? 'Obteniendo ubicación...' : 'Usar mi ubicación'}
        </button>

        <div className="flex flex-1 gap-2">
          <input
            type="text"
            aria-label="Dirección para filtrar cobertura"
            placeholder="Ingresa tu dirección para ver locales cercanos..."
            value={address}
            onChange={(event) => setAddress(event.target.value)}
            disabled={busy}
            className="min-w-0 flex-1 rounded-control border border-border bg-canvas px-3 py-2 text-sm text-foreground placeholder:text-foreground-muted focus:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
          />
          <button
            type="button"
            onClick={() => void handleSearchAddress()}
            disabled={busy || address.trim() === ''}
            className="inline-flex shrink-0 items-center justify-center rounded-control bg-primary px-3.5 py-2 text-sm font-semibold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
          >
            {searching ? 'Buscando...' : 'Buscar dirección'}
          </button>
        </div>
      </div>

      {coordinates !== null ? (
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-foreground-secondary">
          <span>
            {showingAll
              ? 'Mostrando todos los locales del catálogo.'
              : 'Mostrando locales con cobertura en tu zona.'}
          </span>
          <div className="flex items-center gap-3">
            {showingAll ? (
              <button
                type="button"
                onClick={() => onToggleShowAll(false)}
                className="font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 rounded"
              >
                Filtrar por mi zona
              </button>
            ) : (
              <button
                type="button"
                onClick={() => onToggleShowAll(true)}
                className="font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 rounded"
              >
                Ver todos los locales
              </button>
            )}
            <button
              type="button"
              onClick={handleClear}
              className="font-medium text-foreground-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2 rounded"
            >
              Quitar ubicación
            </button>
          </div>
        </div>
      ) : null}

      {errorMessage !== null ? (
        <p role="alert" className="text-xs font-medium text-danger">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

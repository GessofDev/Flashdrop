import { useEffect, useState } from 'react';

import { asApiError, storeCatalogApi } from '@/api';
import type { ApiError } from '@/api';
import type { OwnerRestaurantResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Badge, Button, Card, Skeleton } from '@/components/ui';
import { geocodeAddress, GeocodeError } from '@/lib/geocode';
import { LocationError, requestBrowserLocation } from '@/lib/geolocation';

/** Message shown when the browser does not answer with a position. */
const LOCATION_FAILED_MESSAGE =
  'No se pudo obtener tu ubicación. Puedes buscar la dirección del local.';

/** Message shown when the geocoder does not answer. */
const GEOCODE_FAILED_MESSAGE = 'No se pudo buscar la dirección. Inténtalo de nuevo.';

/** Message shown when the coordinates typed are not numbers. */
const INVALID_COORDINATES_MESSAGE = 'Ingresa una latitud y una longitud válidas.';

/** Message shown when the radius typed is not a number. */
const INVALID_RADIUS_MESSAGE = 'Ingresa un radio de despacho válido.';

const INPUT_CLASSES =
  'h-10 w-full rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted';

const LABEL_CLASSES = 'text-sm font-semibold text-foreground';

/** Message of the last attempt of a form, with the tone it deserves. */
interface FormStatus {
  /** Copy shown to the owner. */
  message: string;
  /** Whether the attempt failed. */
  failed: boolean;
}

/** Text of a value of the backend, or null when the backend has none. */
function textOf(value: number | null | undefined): string | null {
  return value === null || value === undefined ? null : String(value);
}

export interface StoreLocationPageProps {
  /** Extra classes of the container of the screen. */
  className?: string;
}

/**
 * Ubicación y despacho of the local.
 *
 * The backend refuses every order of a local without coordinates or without
 * a radius of dispatch, so the owner configures both here. The two values
 * live in two endpoints, so they live in two forms: each one is saved on its
 * own and shows what the backend answered.
 *
 * The coordinates come from the browser or, when the owner does not share
 * its location, from the geocoding of the address of the local — the same
 * modules the checkout of the customer uses.
 */
export function StoreLocationPage({ className }: StoreLocationPageProps = {}) {
  const [restaurant, setRestaurant] = useState<OwnerRestaurantResponse | null>(null);
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [radius, setRadius] = useState('');

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<ApiError | null>(null);

  const [locationError, setLocationError] = useState<ApiError | null>(null);
  const [locationStatus, setLocationStatus] = useState<FormStatus | null>(null);
  const [savingLocation, setSavingLocation] = useState(false);

  const [radiusError, setRadiusError] = useState<ApiError | null>(null);
  const [radiusStatus, setRadiusStatus] = useState<FormStatus | null>(null);
  const [savingRadius, setSavingRadius] = useState(false);

  const [locating, setLocating] = useState(false);
  const [searching, setSearching] = useState(false);

  /** Show what the backend holds of the local. */
  const applyRestaurant = (answer: OwnerRestaurantResponse): void => {
    setRestaurant(answer);
    setLatitude(textOf(answer.latitude) ?? '');
    setLongitude(textOf(answer.longitude) ?? '');
    setRadius(textOf(answer.deliveryRadiusKm) ?? '');
  };

  useEffect(() => {
    let active = true;

    storeCatalogApi
      .getMyRestaurant()
      .then((answer) => {
        if (active) {
          applyRestaurant(answer);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setLoadError(asApiError(cause));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  /** Ask the browser where the owner is. */
  const handleBrowserLocation = async (): Promise<void> => {
    setLocating(true);
    setLocationStatus(null);

    try {
      const coordinates = await requestBrowserLocation();

      setLatitude(String(coordinates.latitude));
      setLongitude(String(coordinates.longitude));
      setLocationStatus({
        message: 'Coordenadas tomadas de tu navegador.',
        failed: false,
      });
    } catch (cause) {
      setLocationStatus({
        message:
          cause instanceof LocationError ? cause.message : LOCATION_FAILED_MESSAGE,
        failed: true,
      });
    } finally {
      setLocating(false);
    }
  };

  /** Resolve the address of the local into coordinates. */
  const handleSearchAddress = async (): Promise<void> => {
    const address = restaurant?.address ?? '';

    if (address.trim().length === 0) {
      setLocationStatus({
        message: 'El local no tiene una dirección escrita: agrégala para buscar su ubicación.',
        failed: true,
      });
      return;
    }

    setSearching(true);
    setLocationStatus(null);

    try {
      const coordinates = await geocodeAddress(address);

      setLatitude(String(coordinates.latitude));
      setLongitude(String(coordinates.longitude));
      setLocationStatus({
        message: 'Coordenadas encontradas con la dirección del local.',
        failed: false,
      });
    } catch (cause) {
      setLocationStatus({
        message: cause instanceof GeocodeError ? cause.message : GEOCODE_FAILED_MESSAGE,
        failed: true,
      });
    } finally {
      setSearching(false);
    }
  };

  /** Save the coordinates of the local. */
  const handleSaveLocation = async (): Promise<void> => {
    const parsedLatitude = Number.parseFloat(latitude);
    const parsedLongitude = Number.parseFloat(longitude);

    setLocationStatus(null);
    setLocationError(null);

    if (!Number.isFinite(parsedLatitude) || !Number.isFinite(parsedLongitude)) {
      setLocationStatus({ message: INVALID_COORDINATES_MESSAGE, failed: true });
      return;
    }

    setSavingLocation(true);

    try {
      const answer = await storeCatalogApi.setLocation(parsedLatitude, parsedLongitude);

      applyRestaurant(answer);
      setLocationStatus({ message: 'Ubicación guardada.', failed: false });
    } catch (cause) {
      setLocationError(asApiError(cause));
    } finally {
      setSavingLocation(false);
    }
  };

  /** Save the radius of dispatch of the local. */
  const handleSaveRadius = async (): Promise<void> => {
    const parsedRadius = Number.parseFloat(radius);

    setRadiusStatus(null);
    setRadiusError(null);

    if (!Number.isFinite(parsedRadius)) {
      setRadiusStatus({ message: INVALID_RADIUS_MESSAGE, failed: true });
      return;
    }

    setSavingRadius(true);

    try {
      const answer = await storeCatalogApi.setDeliveryRadius(parsedRadius);

      applyRestaurant(answer);
      setRadiusStatus({ message: 'Radio de despacho guardado.', failed: false });
    } catch (cause) {
      setRadiusError(asApiError(cause));
    } finally {
      setSavingRadius(false);
    }
  };

  const hasCoordinates =
    restaurant?.latitude !== null &&
    restaurant?.latitude !== undefined &&
    restaurant?.longitude !== null &&
    restaurant?.longitude !== undefined;
  const hasRadius =
    restaurant?.deliveryRadiusKm !== null && restaurant?.deliveryRadiusKm !== undefined;
  const covered = hasCoordinates && hasRadius;

  return (
    <div
      className={`mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 md:px-8 ${className ?? ''}`}
    >
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
          Ubicación y despacho
        </h1>
        <p className="text-sm text-foreground-secondary">
          Define dónde está tu local y hasta dónde despachas. Sin coordenadas y sin radio de
          despacho, el backend rechaza todos los pedidos de tu local: no puede calcular el envío
          ni validar la cobertura.
        </p>
      </header>

      {loadError !== null ? <ApiErrorNotice error={loadError} /> : null}

      {loading ? <Skeleton label="Cargando la cobertura del local" blocks={2} height="h-40" /> : null}

      {restaurant !== null ? (
        <>
          <Card className="flex flex-col gap-2 p-5">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="font-display text-base font-bold text-foreground">
                Cobertura actual
              </h2>
              <Badge variant={covered ? 'success' : 'warning'}>
                {covered ? 'Cobertura configurada' : 'Cobertura incompleta'}
              </Badge>
            </div>
            <p className="text-sm text-foreground-secondary">
              Latitud:{' '}
              <span className="nums-tabular font-semibold text-foreground">
                {textOf(restaurant.latitude) ?? 'Sin configurar'}
              </span>
            </p>
            <p className="text-sm text-foreground-secondary">
              Longitud:{' '}
              <span className="nums-tabular font-semibold text-foreground">
                {textOf(restaurant.longitude) ?? 'Sin configurar'}
              </span>
            </p>
            <p className="text-sm text-foreground-secondary">
              Radio de despacho:{' '}
              <span className="nums-tabular font-semibold text-foreground">
                {textOf(restaurant.deliveryRadiusKm) === null
                  ? 'Sin configurar'
                  : `${textOf(restaurant.deliveryRadiusKm)} km`}
              </span>
            </p>
          </Card>

          <Card className="flex flex-col gap-4 p-5">
            <div className="flex flex-col gap-1">
              <h2 className="font-display text-base font-bold text-foreground">
                Ubicación del local
              </h2>
              <p className="text-sm text-foreground-secondary">
                Son las coordenadas con las que el backend calcula la distancia y valida la
                cobertura del local. Puedes tomarlas de tu navegador o buscarlas con la dirección
                del local.
              </p>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <label className={LABEL_CLASSES} htmlFor="store-location-latitude">
                  Latitud
                </label>
                <input
                  id="store-location-latitude"
                  name="latitude"
                  type="number"
                  step="any"
                  min="-90"
                  max="90"
                  value={latitude}
                  onChange={(event) => setLatitude(event.target.value)}
                  className={INPUT_CLASSES}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className={LABEL_CLASSES} htmlFor="store-location-longitude">
                  Longitud
                </label>
                <input
                  id="store-location-longitude"
                  name="longitude"
                  type="number"
                  step="any"
                  min="-180"
                  max="180"
                  value={longitude}
                  onChange={(event) => setLongitude(event.target.value)}
                  className={INPUT_CLASSES}
                />
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={locating}
                onClick={() => {
                  void handleBrowserLocation();
                }}
              >
                {locating ? 'Buscando tu ubicación...' : 'Usar mi ubicación'}
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={searching}
                onClick={() => {
                  void handleSearchAddress();
                }}
              >
                {searching ? 'Buscando la dirección...' : 'Buscar dirección'}
              </Button>
            </div>

            {locationError !== null ? <ApiErrorNotice error={locationError} /> : null}
            {locationStatus !== null ? (
              <p
                role={locationStatus.failed ? 'alert' : 'status'}
                className={`text-sm ${
                  locationStatus.failed
                    ? 'font-semibold text-destructive'
                    : 'font-medium text-success'
                }`}
              >
                {locationStatus.message}
              </p>
            ) : null}

            <Button
              onClick={() => {
                void handleSaveLocation();
              }}
              disabled={savingLocation}
              className="w-fit"
            >
              {savingLocation ? 'Guardando...' : 'Guardar ubicación'}
            </Button>
          </Card>

          <Card className="flex flex-col gap-4 p-5">
            <div className="flex flex-col gap-1">
              <h2 className="font-display text-base font-bold text-foreground">
                Radio de despacho
              </h2>
              <p className="text-sm text-foreground-secondary">
                Es la distancia máxima a la que despachas, en kilómetros. El backend lo valida
                entre 1 y 50 km, con hasta dos decimales, y lo usa para decidir si un pedido está
                dentro de tu cobertura.
              </p>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className={LABEL_CLASSES} htmlFor="store-delivery-radius">
                Radio de despacho (km)
              </label>
              <input
                id="store-delivery-radius"
                name="delivery_radius_km"
                type="number"
                min="1"
                max="50"
                step="0.01"
                value={radius}
                onChange={(event) => setRadius(event.target.value)}
                className={INPUT_CLASSES}
              />
            </div>

            {radiusError !== null ? <ApiErrorNotice error={radiusError} /> : null}
            {radiusStatus !== null ? (
              <p
                role={radiusStatus.failed ? 'alert' : 'status'}
                className={`text-sm ${
                  radiusStatus.failed
                    ? 'font-semibold text-destructive'
                    : 'font-medium text-success'
                }`}
              >
                {radiusStatus.message}
              </p>
            ) : null}

            <Button
              onClick={() => {
                void handleSaveRadius();
              }}
              disabled={savingRadius}
              className="w-fit"
            >
              {savingRadius ? 'Guardando...' : 'Guardar radio'}
            </Button>
          </Card>
        </>
      ) : null}
    </div>
  );
}

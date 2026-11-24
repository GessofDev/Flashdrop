import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useEffect, useRef, useState } from 'react';

import { asApiError, courierApi } from '@/api';
import type { ApiError, RouteLocationResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { formatOrderDate } from '@/lib/format';
import { routeStatusLabel } from '@/lib/routeStatus';

/** Polling interval for the courier's live location, in milliseconds (spec S7). */
const LOCATION_POLL_INTERVAL_MS = 8_000;

/** Default zoom level when centering on the courier's position. */
const MAP_ZOOM = 15;

export interface LiveRouteMapProps {
  /** Numeric id of the delivery route in `delivery-service`. */
  routeId: number;
  /** Address of the restaurant, where the order is picked up. */
  pickupAddress: string;
  /** Address of the customer, where the order is delivered. */
  deliveryAddress: string;
  /** Status of the route, as the backend labels it. */
  status: string;
}

/**
 * Live courier tracking map using Leaflet + OpenStreetMap tiles (spec S7, T8).
 *
 * Polls `GET /api/delivery/routes/{routeId}/location` every 8 seconds while the
 * order is active and renders the courier marker on an OSM tile layer with
 * visible attribution.
 */
export function LiveRouteMap({
  routeId,
  pickupAddress,
  deliveryAddress,
  status,
}: LiveRouteMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.CircleMarker | null>(null);

  const [location, setLocation] = useState<RouteLocationResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    let active = true;

    const fetchLocation = async (): Promise<void> => {
      try {
        const next = await courierApi.getRouteLocation(routeId);
        if (active) {
          setLocation(next);
          setError(null);
          setLoading(false);
        }
      } catch (cause: unknown) {
        if (active) {
          setError(asApiError(cause));
          setLoading(false);
        }
      }
    };

    setLoading(true);
    void fetchLocation();

    const timer = window.setInterval(() => {
      void fetchLocation();
    }, LOCATION_POLL_INTERVAL_MS);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [routeId]);

  useEffect(() => {
    if (containerRef.current === null || location === null) {
      return;
    }

    const coords: [number, number] = [location.latitude, location.longitude];

    if (mapRef.current === null) {
      const map = L.map(containerRef.current).setView(coords, MAP_ZOOM);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19,
      }).addTo(map);
      markerRef.current = L.circleMarker(coords, {
        radius: 9,
        color: 'var(--color-surface)',
        weight: 2,
        fillColor: 'var(--color-primary)',
        fillOpacity: 1,
      }).addTo(map);
      mapRef.current = map;
      return;
    }

    mapRef.current.setView(coords, MAP_ZOOM);
    markerRef.current?.setLatLng(coords);
  }, [location]);

  useEffect(() => {
    return () => {
      mapRef.current?.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
  }, []);

  const waitingForFirstPing = error !== null && error.status === 404;

  return (
    <section
      aria-labelledby="route-map-title"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="route-map-title" className="font-display text-lg font-bold text-foreground">
          Ruta de entrega
        </h2>
        <span className="flex items-center gap-2 text-xs text-foreground-secondary">
          <span className="label-code text-foreground-muted">Estado de la ruta</span>
          <span className="font-bold text-foreground">{routeStatusLabel(status)}</span>
        </span>
      </div>

      {location !== null ? (
        <div className="overflow-hidden rounded-container border border-border bg-canvas">
          <div
            ref={containerRef}
            aria-label="Mapa en vivo del repartidor"
            className="h-56 w-full md:h-72"
          />
        </div>
      ) : waitingForFirstPing ? (
        <p className="rounded-container border border-dashed border-border bg-canvas p-6 text-center text-sm text-foreground-secondary">
          Esperando la primera ubicación del repartidor...
        </p>
      ) : loading ? (
        <p className="rounded-container border border-dashed border-border bg-canvas p-6 text-center text-sm text-foreground-secondary">
          Consultando ubicación del repartidor...
        </p>
      ) : error !== null ? (
        <ApiErrorNotice error={error} />
      ) : null}

      <dl className="flex flex-col gap-2 text-sm">
        <div className="flex flex-col gap-0.5">
          <dt className="label-code text-xs text-foreground-muted">Origen</dt>
          <dd className="text-foreground-secondary">{pickupAddress}</dd>
        </div>
        <div className="flex flex-col gap-0.5">
          <dt className="label-code text-xs text-foreground-muted">Destino</dt>
          <dd className="text-foreground-secondary">{deliveryAddress}</dd>
        </div>
      </dl>

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-foreground-muted">
        <span>
          {location !== null
            ? `Última posición: ${formatOrderDate(location.recordedAt)}`
            : 'Seguimiento en vivo activo'}
        </span>
        <span>Mapa: © OpenStreetMap contributors</span>
      </div>
    </section>
  );
}

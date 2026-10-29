import { routeStatusLabel } from '@/lib/routeStatus';

export interface StaticRouteMapProps {
  /** Address of the restaurant, where the order is picked up. */
  pickupAddress: string;
  /** Address of the customer, where the order is delivered. */
  deliveryAddress: string;
  /** Status of the route, as the backend labels it. */
  status: string;
}

/**
 * Schematic view of the route of the delivery (spec S10).
 *
 * The backend sends the two addresses and nothing else: there are no
 * coordinates and no telemetry, so the drawing is a **schematic** one — the
 * origin, the destination and the way between them. It is not a map of the
 * city, and it never shows a position, a speed or an arrival time that the
 * backend did not send.
 */
export function StaticRouteMap({ pickupAddress, deliveryAddress, status }: StaticRouteMapProps) {
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

      <div className="overflow-hidden rounded-container border border-border bg-canvas">
        <svg
          role="img"
          aria-label="Mapa esquemático de la ruta, del restaurante a la dirección de entrega"
          viewBox="0 0 640 300"
          className="h-56 w-full text-border md:h-72"
          preserveAspectRatio="xMidYMid slice"
        >
          <path
            d="M0 150 H640"
            stroke="currentColor"
            strokeWidth="16"
            strokeLinecap="round"
            fill="none"
          />
          <path
            d="M210 0 V300"
            stroke="currentColor"
            strokeWidth="10"
            strokeLinecap="round"
            fill="none"
          />
          <path
            d="M430 0 V300"
            stroke="currentColor"
            strokeWidth="10"
            strokeLinecap="round"
            fill="none"
          />
          <path
            d="M70 60 H570"
            stroke="currentColor"
            strokeWidth="4"
            strokeLinecap="round"
            fill="none"
            opacity="0.6"
          />
          <path
            d="M80 240 H560"
            stroke="currentColor"
            strokeWidth="4"
            strokeLinecap="round"
            fill="none"
            opacity="0.6"
          />
          <path
            d="M210 150 C 270 100, 330 200, 430 150"
            className="text-primary"
            stroke="currentColor"
            strokeWidth="5"
            strokeDasharray="10 8"
            strokeLinecap="round"
            fill="none"
          />
          <circle cx="210" cy="150" r="13" className="text-foreground" fill="currentColor" />
          <circle cx="430" cy="150" r="13" className="text-success" fill="currentColor" />
        </svg>
      </div>

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

      <p className="text-xs text-foreground-muted">
        Vista esquemática de la ruta: el backend no envía posiciones en vivo.
      </p>
    </section>
  );
}

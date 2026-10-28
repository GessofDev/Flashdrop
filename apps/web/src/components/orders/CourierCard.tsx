import { MISSING_VALUE } from '@/lib/format';

export interface CourierCardProps {
  /** Name of the courier. */
  name: string;
  /** Phone of the courier, when the backend sent one. */
  phone: string | null;
  /** Vehicle of the courier, when the backend sent one. */
  vehicle: string | null;
}

/**
 * Courier of the order (spec S10).
 *
 * The detail of the order sends the name, the phone and the vehicle of the
 * courier — and nothing else. There is no score, no number of deliveries and
 * no position, so the card shows the name and the vehicle: the app does not
 * invent a score nor a live location.
 */
export function CourierCard({ name, phone, vehicle }: CourierCardProps) {
  return (
    <section
      aria-labelledby="courier-title"
      className="flex flex-col gap-3 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <h2 id="courier-title" className="font-display text-lg font-bold text-foreground">
        Repartidor
      </h2>

      <div className="flex flex-col gap-1">
        <span className="font-display text-base font-bold text-foreground">{name}</span>
      </div>

      <dl className="flex flex-col gap-2 text-sm">
        <div className="flex items-center justify-between gap-3">
          <dt className="label-code text-xs text-foreground-muted">Vehículo</dt>
          <dd className="font-semibold text-foreground">{vehicle ?? MISSING_VALUE}</dd>
        </div>
        {phone !== null && phone.trim().length > 0 ? (
          <div className="flex items-center justify-between gap-3">
            <dt className="label-code text-xs text-foreground-muted">Teléfono</dt>
            <dd className="nums-tabular text-foreground-secondary">{phone}</dd>
          </div>
        ) : null}
      </dl>
    </section>
  );
}

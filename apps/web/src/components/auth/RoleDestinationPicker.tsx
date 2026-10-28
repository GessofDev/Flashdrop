import type { Role } from '@/session';

export interface RoleDestinationPickerProps {
  /** Destination the user chose. */
  value: Role;
  /** Called with the destination the user selects. */
  onChange: (role: Role) => void;
}

const DESTINATIONS = [
  { role: 'CLIENTE', label: 'Cliente', hint: 'Pedir comida' },
  { role: 'RESTAURANTE', label: 'Mi Tienda', hint: 'KDS y Cocina' },
  { role: 'REPARTIDOR', label: 'Repartidor', hint: 'Flota Courier' },
] as const satisfies ReadonlyArray<{ role: Role; label: string; hint: string }>;

/**
 * Module of preferred destination of the login.
 *
 * It chooses where the user lands after signing in; it does **not** grant a
 * role: the backend assigns the roles of the account on its own
 * (`PUT /api/internal/users/{userId}/roles`).
 */
export function RoleDestinationPicker({ value, onChange }: RoleDestinationPickerProps) {
  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-semibold text-foreground">Módulo de destino preferente</span>
      <div
        role="group"
        aria-label="Módulo de destino preferente"
        className="grid grid-cols-3 gap-2 rounded-container bg-surface-hover p-1.5"
      >
        {DESTINATIONS.map((destination) => {
          const active = destination.role === value;

          return (
            <button
              key={destination.role}
              type="button"
              aria-label={destination.label}
              aria-pressed={active}
              onClick={() => onChange(destination.role)}
              className={[
                'flex flex-col items-center justify-center gap-0.5 rounded-control px-3 py-3 transition-colors',
                active
                  ? 'bg-surface text-primary shadow-card'
                  : 'text-foreground-secondary hover:bg-surface/60 hover:text-foreground',
              ].join(' ')}
            >
              <span className="text-sm font-semibold">{destination.label}</span>
              <span className="text-xs font-normal text-foreground-muted">{destination.hint}</span>
            </button>
          );
        })}
      </div>
      <p className="text-xs text-foreground-secondary">
        Podrás conmutar de rol al instante una vez dentro, sin cerrar sesión.
      </p>
    </div>
  );
}

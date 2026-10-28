import { useState } from 'react';

import type { CourierVehicle } from '@/api/delivery';
import { Button } from '@/components/ui';

export interface CourierProfileFormProps {
  /** Whether the profile creation is in flight. */
  pending: boolean;
  /** Create the profile of the authenticated courier. */
  onSubmit: (vehicle: CourierVehicle) => void;
}

/** Recovery form shown when the courier profile is missing. */
export function CourierProfileForm({ pending, onSubmit }: CourierProfileFormProps) {
  const [vehicle, setVehicle] = useState<CourierVehicle>('MOTO');

  return (
    <section
      aria-labelledby="courier-profile-title"
      className="rounded-container border border-warning bg-warning-surface p-5"
    >
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div className="flex flex-col gap-1">
          <h2 id="courier-profile-title" className="font-display text-lg font-bold text-foreground">
            Crear perfil de repartidor
          </h2>
          <p className="text-sm text-foreground-secondary">
            Registra tu vehículo para poder tomar pedidos.
          </p>
        </div>

        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-foreground" htmlFor="courier-vehicle">
              Vehículo
            </label>
            <select
              id="courier-vehicle"
              value={vehicle}
              onChange={(event) => {
                setVehicle(event.target.value as CourierVehicle);
              }}
              className="h-10 rounded-control border border-border bg-surface px-3 text-sm text-foreground"
            >
              <option value="MOTO">Moto</option>
              <option value="AUTO">Auto</option>
              <option value="BICICLETA">Bicicleta</option>
              <option value="PIE">A pie</option>
            </select>
          </div>

          <Button disabled={pending} onClick={() => onSubmit(vehicle)}>
            Crear perfil de repartidor
          </Button>
        </div>
      </div>
    </section>
  );
}

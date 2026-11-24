import { useSession } from '@/session';

import { AdminCouriersPanel } from './AdminCouriersPanel';
import { AdminPolicyPanel } from './AdminPolicyPanel';
import { AdminRestaurantsPanel } from './AdminRestaurantsPanel';
import { AdminTariffPanel } from './AdminTariffPanel';
import { AdminUsersPanel } from './AdminUsersPanel';

/**
 * Dedicated administration section (spec S11, T12).
 *
 * Accessible only to accounts holding all three roles (`CLIENTE`,
 * `RESTAURANTE`, `REPARTIDOR`), separate from the role-specific screens.
 */
export function AdminPage() {
  const { session } = useSession();
  const roles = session?.roles ?? [];
  const isAdmin =
    roles.includes('CLIENTE') && roles.includes('RESTAURANTE') && roles.includes('REPARTIDOR');

  if (!isAdmin) {
    return (
      <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
        <div className="rounded-container border border-border bg-surface p-6 shadow-card">
          <h1 className="font-display text-xl font-bold text-foreground">Administración</h1>
          <p className="mt-2 text-sm text-foreground-secondary">
            Solo los administradores (cuentas con los tres roles) pueden acceder a esta sección.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-app flex-col gap-6 px-4 py-6 md:px-8">
      <header className="flex flex-col gap-1">
        <p className="label-code text-xs text-primary">Administración</p>
        <h1 className="font-display text-2xl font-bold text-foreground">
          Panel de administración
        </h1>
        <p className="text-sm text-foreground-secondary">
          Gestión de usuarios, restaurantes, repartidores, tarifa de despacho y plazos globales.
        </p>
      </header>

      <AdminUsersPanel />
      <AdminRestaurantsPanel />
      <AdminCouriersPanel />
      <AdminTariffPanel />
      <AdminPolicyPanel />
    </div>
  );
}

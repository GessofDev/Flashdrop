import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { AppRoutes } from '@/app/AppRoutes';
import { routePaths } from '@/app/routePaths';
import { CartProvider } from '@/cart';
import { SessionProvider, sessionStore } from '@/session';
import type { Role } from '@/session';

import { signIn } from './support/renderApp';

const ROUTES: ReadonlyArray<{ path: string; heading: string; role: Role }> = [
  { path: routePaths.marketplace, heading: 'Pide en minutos con FlashDrop Chile', role: 'CLIENTE' },
  { path: routePaths.orders, heading: 'Mis pedidos', role: 'CLIENTE' },
  { path: routePaths.storeKds, heading: 'Monitor de cocina', role: 'RESTAURANTE' },
  { path: routePaths.storeCatalog, heading: 'Catálogo y menú', role: 'RESTAURANTE' },
  {
    path: routePaths.storeDispatchHistory,
    heading: 'Historial de despachos',
    role: 'RESTAURANTE',
  },
  { path: routePaths.storeAnalytics, heading: 'Analíticas de ventas', role: 'RESTAURANTE' },
  { path: routePaths.courier, heading: 'Consola del repartidor', role: 'REPARTIDOR' },
  {
    path: routePaths.courierAvailable,
    heading: 'Pedidos disponibles',
    role: 'REPARTIDOR',
  },
  { path: routePaths.courierHistory, heading: 'Historial de entregas', role: 'REPARTIDOR' },
  { path: routePaths.profile, heading: 'Perfil', role: 'CLIENTE' },
];

function renderRoutes(path: string) {
  return render(
    <SessionProvider>
      <CartProvider>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
        </MemoryRouter>
      </CartProvider>
    </SessionProvider>,
  );
}

describe('router skeleton', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  it.each(ROUTES)('renders the $heading placeholder at $path', ({ path, heading, role }) => {
    signIn({ activeRole: role });

    renderRoutes(path);

    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
  });

  it('renders a not-found placeholder for an unknown route', () => {
    signIn({ activeRole: 'CLIENTE' });

    renderRoutes('/ruta-inexistente');

    expect(screen.getByRole('heading', { level: 1, name: 'Página no encontrada' })).toBeInTheDocument();
  });
});

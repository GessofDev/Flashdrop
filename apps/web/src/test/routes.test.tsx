import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AppRoutes } from '@/app/AppRoutes';
import { routePaths } from '@/app/routePaths';

const ROUTES: ReadonlyArray<{ path: string; heading: string }> = [
  { path: routePaths.marketplace, heading: 'Marketplace' },
  { path: routePaths.orders, heading: 'Mis pedidos' },
  { path: routePaths.storeKds, heading: 'Monitor de cocina' },
  { path: routePaths.storeCatalog, heading: 'Catálogo y menú' },
  { path: routePaths.storeAnalytics, heading: 'Analíticas de ventas' },
  { path: routePaths.courier, heading: 'Consola del repartidor' },
  { path: routePaths.profile, heading: 'Perfil' },
  { path: routePaths.login, heading: 'Iniciar sesión' },
];

describe('router skeleton', () => {
  it.each(ROUTES)('renders the $heading placeholder at $path', ({ path, heading }) => {
    render(
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
  });

  it('renders a not-found placeholder for an unknown route', () => {
    render(
      <MemoryRouter initialEntries={['/ruta-inexistente']}>
        <AppRoutes />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { level: 1, name: 'Página no encontrada' })).toBeInTheDocument();
  });
});

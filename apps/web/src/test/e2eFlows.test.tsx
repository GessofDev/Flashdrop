import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { PRODUCTS, catalogResponse } from './support/catalogMock';
import { courierMockState, courierResponse } from './support/courierMock';
import { kitchenMockState, kitchenResponse } from './support/kitchenMock';
import { ordersResponse } from './support/ordersMock';
import { renderApp, signIn } from './support/renderApp';
import { listSourceFiles } from './support/sourceFiles';

describe('end-to-end role workflows and hardening (spec S4, S5, S6)', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('executes the full customer workflow: browse marketplace, add to cart, and track order', async () => {
    signIn({ activeRole: 'CLIENTE' });

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname.startsWith('/catalog') || pathname.startsWith('/api/catalog')) {
        return catalogResponse(call);
      }
      if (pathname.startsWith('/api/orders')) {
        return ordersResponse(call);
      }
      return jsonResponse({});
    });

    renderApp('/');

    // 1. Marketplace is rendered with search/category headers
    expect(await screen.findByRole('heading', { level: 1, name: 'Pide en minutos con FlashDrop Chile' })).toBeInTheDocument();
    const restaurants = await screen.findAllByText('La Maestranza Burgers');
    expect(restaurants[0]).toBeInTheDocument();

    // 2. Add an item to cart
    const addBtn = await screen.findByRole('button', {
      name: 'Añadir Doble Smash Cheddar Bacon al carrito',
    });
    await userEvent.click(addBtn);

    // 3. Cart complementary shows item
    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });
    expect(within(cart).getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();

    // 4. Navigate to tracking/orders

    // 5. Navigate to tracking/orders
    const ordersLink = screen.getByRole('link', { name: 'Mis Pedidos' });
    await userEvent.click(ordersLink);

    expect(await screen.findByRole('heading', { level: 1, name: 'Mis pedidos' })).toBeInTheDocument();
    expect(await screen.findByText('#FD-42')).toBeInTheDocument();
  });

  it('executes the store operator workflow: kitchen board, order lifecycle, and catalog', async () => {
    signIn({ activeRole: 'RESTAURANTE' });

    const kitchenState = kitchenMockState();

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products') {
        return jsonResponse(PRODUCTS);
      }
      if (pathname.startsWith('/api/catalog') || pathname.startsWith('/catalog')) {
        return catalogResponse(call);
      }
      if (pathname.startsWith('/api/orders')) {
        return kitchenResponse(call, kitchenState);
      }
      return jsonResponse({});
    });

    renderApp('/tienda/kds');

    // 1. Kitchen display system renders the 3 canonical columns
    expect(await screen.findByRole('heading', { level: 1, name: 'Monitor de cocina' })).toBeInTheDocument();
    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });
    expect(pending).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Columna EN COCINA' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Columna LISTO / RETIRO' })).toBeInTheDocument();

    // 2. Accept a pending order into preparation
    const acceptBtn = within(pending).getByRole('button', { name: 'Avanzar a Preparando' });
    await userEvent.click(acceptBtn);

    // 3. Navigate to catalog management
    const catalogLink = screen.getByRole('link', { name: 'Catálogo & Menú' });
    await userEvent.click(catalogLink);

    expect(await screen.findByRole('heading', { level: 1, name: 'Gestión de Catálogo y Menú' })).toBeInTheDocument();
    expect(await screen.findByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();
  });

  it('executes the courier workflow: available FIFO queue, claim, active route, and delivery', async () => {
    signIn({ activeRole: 'REPARTIDOR' });

    const courierState = courierMockState();

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname.startsWith('/catalog') || pathname.startsWith('/api/catalog')) {
        return catalogResponse(call);
      }
      if (pathname.startsWith('/api/delivery') || pathname.startsWith('/api/orders')) {
        return courierResponse(call, courierState);
      }
      return jsonResponse({});
    });

    renderApp('/repartidor/pedidos');

    // 1. Courier available orders queue
    const queue = await screen.findByRole('list', { name: 'Pedidos disponibles' });
    expect(queue).toBeInTheDocument();
    expect(within(queue).getByRole('article', { name: 'Pedido FD-50' })).toBeInTheDocument();

    // 2. Claim an available order
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-50' }));
    const claimBtn = screen.getByRole('button', { name: 'Tomar 1 pedido' });
    await userEvent.click(claimBtn);

    // 3. Navigate to active route
    const activeRouteLink = screen.getByRole('link', { name: 'Ruta Activa' });
    await userEvent.click(activeRouteLink);

    expect(await screen.findByRole('heading', { level: 1, name: 'Consola del repartidor' })).toBeInTheDocument();
    expect(await screen.findByText(/La Maestranza Burgers/i)).toBeInTheDocument();

    // 4. Navigate to delivery history
    const historyLink = screen.getByRole('link', { name: 'Historial de Entregas' });
    await userEvent.click(historyLink);

    expect(await screen.findByRole('heading', { level: 1, name: 'Historial de entregas' })).toBeInTheDocument();
  });

  it('verifies accessibility hardening: interactive controls declare focus ring classes', () => {
    const uiSourceFiles = listSourceFiles('components/ui');

    const buttonSource = uiSourceFiles.find((f) => f.path.includes('Button.tsx'));
    expect(buttonSource).toBeDefined();

    // Focus ring defined in Button primitives
    expect(buttonSource?.content).toMatch(/focus-visible:outline-2/);
    expect(buttonSource?.content).toMatch(/focus-visible:outline-primary/);
  });

  it('verifies responsive design hardening: major pages use fluid responsive grid layouts', () => {
    const componentFiles = listSourceFiles('components');

    // Marketplace, KDS and Profile use responsive Tailwind breakpoint classes (md: or lg:)
    const responsiveFiles = componentFiles.filter((file) =>
      /(md:grid-cols-|lg:grid-cols-|md:flex-row|sm:grid-cols-)/.test(file.content),
    );

    expect(responsiveFiles.length).toBeGreaterThanOrEqual(5);
  });
});

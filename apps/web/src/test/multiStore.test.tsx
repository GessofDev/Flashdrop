import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { request } from '@/api';
import { formatRestaurantName } from '@/lib/format';
import { sessionStore } from '@/session';
import { storeContextStore } from '@/session/storeContextStore';

import { CATEGORIES } from './support/catalogMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const MY_STORES = [
  {
    id: 101,
    name: 'Providencia',
    merchantName: 'La Maestranza',
    address: 'Av. Providencia 1234',
    phone: null,
    image: null,
    open: true,
    acceptingOrders: true,
    hours: [{ day: 1, opensAt: '09:00', closesAt: '18:00' }],
  },
  {
    id: 102,
    name: 'Ñuñoa',
    merchantName: 'La Maestranza',
    address: 'Av. Irarrázaval 2500',
    phone: null,
    image: null,
    open: false,
    acceptingOrders: false,
    hours: [{ day: 2, opensAt: '10:00', closesAt: '20:00' }],
  },
];

describe('merchant multi-local (T8)', () => {
  beforeEach(() => {
    sessionStore.reset();
    storeContextStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('formats restaurant names as Marca — Sucursal only when merchantName differs', () => {
    expect(formatRestaurantName({ name: 'Providencia', merchantName: 'La Maestranza' })).toBe(
      'La Maestranza — Providencia',
    );
    expect(formatRestaurantName({ name: 'La Maestranza', merchantName: 'La Maestranza' })).toBe(
      'La Maestranza',
    );
    expect(formatRestaurantName({ name: 'La Maestranza', merchantName: null })).toBe(
      'La Maestranza',
    );
  });

  it('attaches X-My-Store-Restaurant when a store is selected and clears it on role change or logout', async () => {
    signIn({ activeRole: 'RESTAURANTE' });
    storeContextStore.setSelectedStoreId(102);

    const fetchMock = mockFetch(() => jsonResponse({ success: true, data: [] }));

    await request({ path: '/api/catalog/my/products', responseShape: 'bare' });
    await request({ path: '/api/orders', responseShape: 'envelope' });

    expect(fetchMock.calls[0]?.headers['x-my-store-restaurant']).toBe('102');
    expect(fetchMock.calls[1]?.headers['x-my-store-restaurant']).toBe('102');

    // Role change clears the selected store ID
    sessionStore.setActiveRole('CLIENTE');
    expect(storeContextStore.getSelectedStoreId()).toBeNull();

    await request({ path: '/api/orders', responseShape: 'envelope' });
    expect(fetchMock.calls[2]?.headers['x-my-store-restaurant']).toBeUndefined();

    // Logout clears the selected store ID
    storeContextStore.setSelectedStoreId(101);
    sessionStore.clear();
    expect(storeContextStore.getSelectedStoreId()).toBeNull();
  });

  it('renders the store selector as Marca — Sucursal and reloads catalog when switching stores', async () => {
    signIn({ activeRole: 'RESTAURANTE' });

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');
      const storeHeader = call.headers['x-my-store-restaurant'];

      if (pathname === '/api/catalog/my/restaurants') {
        return jsonResponse(MY_STORES);
      }

      if (pathname === '/catalog/categories') {
        return jsonResponse(CATEGORIES);
      }

      if (pathname === '/api/catalog/my/products') {
        if (storeHeader === '102') {
          return jsonResponse([
            {
              id: 20,
              categoryId: 3,
              restaurantId: 102,
              name: 'Burger Ñuñoa Especial',
              description: 'Solo en Ñuñoa',
              price: 8990,
              image: null,
              available: true,
            },
          ]);
        }

        return jsonResponse([
          {
            id: 10,
            categoryId: 3,
            restaurantId: 101,
            name: 'Burger Providencia Clásica',
            description: 'Solo en Providencia',
            price: 7990,
            image: null,
            available: true,
          },
        ]);
      }

      return jsonResponse({ status: 404, error: 'NOT_FOUND', message: 'Not found' }, 404);
    });

    renderApp('/tienda/catalogo');

    const selector = await screen.findByRole('combobox', { name: 'Local activo' });
    expect(within(selector).getByRole('option', { name: 'La Maestranza — Providencia' })).toBeInTheDocument();
    expect(within(selector).getByRole('option', { name: 'La Maestranza — Ñuñoa' })).toBeInTheDocument();

    expect(await screen.findByText('Burger Providencia Clásica')).toBeInTheDocument();

    await userEvent.selectOptions(selector, '102');

    expect(await screen.findByText('Burger Ñuñoa Especial')).toBeInTheDocument();
    expect(screen.queryByText('Burger Providencia Clásica')).toBeNull();

    const productCalls102 = fetchMock.calls.filter(
      (c) => c.url.includes('/api/catalog/my/products') && c.headers['x-my-store-restaurant'] === '102',
    );
    expect(productCalls102.length).toBeGreaterThanOrEqual(1);
  });

  it('reloads store hours and kitchen orders when switching stores', async () => {
    signIn({ activeRole: 'RESTAURANTE' });

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');
      const storeHeader = call.headers['x-my-store-restaurant'];

      if (pathname === '/api/catalog/my/restaurants') {
        return jsonResponse(MY_STORES);
      }

      if (pathname === '/api/catalog/my/restaurant') {
        return jsonResponse(storeHeader === '102' ? MY_STORES[1] : MY_STORES[0]);
      }

      if (pathname === '/api/orders') {
        return jsonResponse({
          success: true,
          data:
            storeHeader === '102'
              ? [
                  {
                    id: 'ord-102',
                    code: 'FD-202',
                    status: 'Nuevo pedido',
                    address: 'Irarrázaval 2500',
                    subtotal: 8990,
                    deliveryFee: 1500,
                    total: 10490,
                    paymentMethod: 'Efectivo',
                    clientName: 'Cliente Ñuñoa',
                    clientPhone: '+56911111111',
                    clientEmail: 'nunoa@test.cl',
                    restaurantName: 'Ñuñoa',
                    deliveryId: null,
                    createdAt: new Date().toISOString(),
                    receptionMode: 'NORMAL',
                  },
                ]
              : [],
        });
      }

      if (pathname.startsWith('/api/orders/')) {
        return jsonResponse({
          success: true,
          data: {
            id: 'ord-102',
            code: 'FD-202',
            status: 'Nuevo pedido',
            address: 'Irarrázaval 2500',
            subtotal: 8990,
            deliveryFee: 1500,
            total: 10490,
            paymentMethod: 'Efectivo',
            createdAt: new Date().toISOString(),
            receptionMode: 'NORMAL',
            client: { name: 'Cliente Ñuñoa', email: null, phone: null },
            restaurant: { name: 'Ñuñoa', address: null },
            delivery: null,
            route: null,
            items: [],
          },
        });
      }

      return jsonResponse({ status: 404, error: 'NOT_FOUND', message: 'Not found' }, 404);
    });

    renderApp('/tienda/horario');

    const selector = await screen.findByRole('combobox', { name: 'Local activo' });
    expect(await screen.findByRole('heading', { name: 'La Maestranza — Providencia' })).toBeInTheDocument();

    await userEvent.selectOptions(selector, '102');
    expect(await screen.findByRole('heading', { name: 'La Maestranza — Ñuñoa' })).toBeInTheDocument();

    // Navigate to KDS and verify orders for store 102 load
    await userEvent.click(screen.getByRole('link', { name: 'KDS Pantalla Cocina' }));
    expect(await screen.findByRole('article', { name: 'Pedido FD-202' })).toBeInTheDocument();
  });

  it('prompts the user to select a store when 400 LOCAL_CONTEXT_REQUIRED is returned', async () => {
    signIn({ activeRole: 'RESTAURANTE' });

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');
      const storeHeader = call.headers['x-my-store-restaurant'];

      if (pathname === '/api/catalog/my/restaurants') {
        return jsonResponse([]);
      }

      if (pathname === '/catalog/categories') {
        return jsonResponse(CATEGORIES);
      }

      if (pathname === '/api/catalog/my/products') {
        if (!storeHeader) {
          return jsonResponse(
            {
              status: 400,
              error: 'LOCAL_CONTEXT_REQUIRED',
              message: 'Debes indicar el local activo en el header X-My-Store-Restaurant',
              availableRestaurants: [
                { id: 101, name: 'Providencia', address: 'Av. Providencia 1234', merchantId: 1 },
                { id: 102, name: 'Ñuñoa', address: 'Av. Irarrázaval 2500', merchantId: 1 },
              ],
            },
            400,
          );
        }

        return jsonResponse([
          {
            id: 20,
            categoryId: 3,
            restaurantId: 102,
            name: 'Burger Ñuñoa Seleccionada',
            description: 'Cargada tras elegir local',
            price: 8990,
            image: null,
            available: true,
          },
        ]);
      }

      return jsonResponse({ status: 404, error: 'NOT_FOUND', message: 'Not found' }, 404);
    });

    renderApp('/tienda/catalogo');

    const prompt = await screen.findByRole('region', { name: 'Seleccionar local activo' });
    expect(within(prompt).getByText('Selecciona un local para operar')).toBeInTheDocument();

    const choiceButton = within(prompt).getByRole('button', { name: /Ñuñoa/ });
    await userEvent.click(choiceButton);

    await waitFor(() => {
      expect(storeContextStore.getSelectedStoreId()).toBe(102);
    });
    expect(await screen.findByText('Burger Ñuñoa Seleccionada')).toBeInTheDocument();
  });

  it('displays Marca — Sucursal in RestaurantCard and RestaurantPage', async () => {
    signIn({ activeRole: 'CLIENTE' });

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/catalog/restaurants') {
        return jsonResponse([
          {
            id: 101,
            name: 'Providencia',
            merchantName: 'La Maestranza',
            address: 'Av. Providencia 1234',
            phone: null,
            image: null,
            open: true,
          },
        ]);
      }

      if (pathname === '/catalog/categories') {
        return jsonResponse(CATEGORIES);
      }

      if (pathname === '/catalog/products') {
        return jsonResponse([]);
      }

      if (pathname.startsWith('/api/reviews/')) {
        return jsonResponse({ average: null, count: 0, distribution: {}, reviews: [] });
      }

      return jsonResponse({ status: 404, error: 'NOT_FOUND', message: 'Not found' }, 404);
    });

    renderApp('/');
    expect(await screen.findByRole('heading', { name: 'La Maestranza — Providencia' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: /La Maestranza — Providencia/ }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'La Maestranza — Providencia' }),
    ).toBeInTheDocument();
  });
});

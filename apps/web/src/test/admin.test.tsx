import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

function adminResponder() {
  const restaurants: Array<{
    id: number;
    name: string;
    address: string | null;
    ownerUserId: number | null;
    acceptingOrders: boolean;
    open: boolean;
    latitude: number | null;
    longitude: number | null;
  }> = [
    {
      id: 1,
      name: 'La Maestranza Burgers',
      address: 'Av. Providencia 1234',
      ownerUserId: 10,
      acceptingOrders: true,
      open: true,
      latitude: -33.44,
      longitude: -70.66,
    },
  ];

  let tariff = {
    dimensionRanges: [{ minDimension: 0, maxDimension: 5, vehicle: 'MOTO', price: 1500 }],
    distanceRanges: [{ minKm: 0, maxKm: 3, price: 1000 }],
  };

  let policy = {
    paymentExpirationMinutes: 15,
    customerCancelWindowMinutes: 10,
    courierWaitMinutes: 20,
  };

  return (call: RecordedCall): Response => {
    const { pathname, searchParams } = new URL(call.url, 'http://flashdrop.test');

    if (call.method === 'GET' && pathname === '/auth/admin/users') {
      const email = searchParams.get('email') ?? '';
      return jsonResponse({
        id: 55,
        name: 'Sofía',
        lastName: 'Rojas',
        email,
        roles: ['Cliente'],
      });
    }

    if (call.method === 'PUT' && pathname === '/auth/admin/users/55/roles') {
      const body = call.body as { roles: string[] };
      return jsonResponse({
        userId: 55,
        roles: body.roles.map((name, idx) => ({ id: idx + 1, name })),
      });
    }

    if (call.method === 'GET' && pathname === '/api/catalog/admin/restaurants') {
      return jsonResponse(restaurants);
    }

    if (call.method === 'POST' && pathname === '/api/catalog/admin/restaurants') {
      const body = call.body as { name: string; address?: string; ownerUserId?: number | null };
      const created = {
        id: 2,
        name: body.name,
        address: body.address ?? null,
        ownerUserId: body.ownerUserId ?? null,
        acceptingOrders: false,
        open: false,
        latitude: null,
        longitude: null,
      };
      restaurants.push(created);
      return jsonResponse(created, 201);
    }

    if (call.method === 'PUT' && pathname === '/api/catalog/admin/restaurants/1/owner') {
      const body = call.body as { userId: number };
      const current = restaurants[0];
      if (current !== undefined) {
        restaurants[0] = { ...current, ownerUserId: body.userId };
      }
      return jsonResponse(restaurants[0]);
    }

    if (call.method === 'POST' && pathname === '/api/delivery/admin/persons') {
      const body = call.body as { userId: number; vehicle: string };
      return jsonResponse(
        {
          success: true,
          message: 'Delivery person profile created',
          data: {
            id: 77,
            userId: String(body.userId),
            vehicle: body.vehicle,
            createdAt: '2026-11-24T20:00:00Z',
          },
        },
        201,
      );
    }

    if (call.method === 'GET' && pathname === '/api/admin/delivery-tariff') {
      return jsonResponse({ success: true, message: 'OK', data: tariff });
    }

    if (call.method === 'PUT' && pathname === '/api/admin/delivery-tariff') {
      tariff = call.body as typeof tariff;
      return jsonResponse({ success: true, message: 'Tarifa actualizada', data: tariff });
    }

    if (call.method === 'GET' && pathname === '/api/admin/order-policy') {
      return jsonResponse({ success: true, message: 'OK', data: policy });
    }

    if (call.method === 'PUT' && pathname === '/api/admin/order-policy') {
      policy = call.body as typeof policy;
      return jsonResponse({ success: true, message: 'Plazos actualizados', data: policy });
    }

    return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
  };
}

/**
 * Administration section (spec S11, T12):
 * accessible at `/admin` only for accounts holding all 3 roles.
 */
describe('administration section', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('blocks non-admin accounts from accessing /admin', async () => {
    signIn({ roles: ['CLIENTE'], activeRole: 'CLIENTE' });
    const fetchMock = mockFetch(adminResponder());

    renderApp('/admin');

    expect(
      await screen.findByText(/Solo los administradores/i),
    ).toBeInTheDocument();
    expect(fetchMock.calls).toHaveLength(0);
  });

  it('lets an admin manage users, restaurants, couriers, tariffs and order policy', async () => {
    signIn({
      roles: ['CLIENTE', 'RESTAURANTE', 'REPARTIDOR'],
      activeRole: 'CLIENTE',
    });
    const fetchMock = mockFetch(adminResponder());

    renderApp('/admin');

    // 1. Users & roles
    const usersSection = await screen.findByRole('region', { name: 'Usuarios y roles' });
    await userEvent.type(within(usersSection).getByLabelText('Correo del usuario'), 'sofia@flashdrop.cl');
    await userEvent.click(within(usersSection).getByRole('button', { name: 'Buscar usuario' }));

    expect(await within(usersSection).findByText('Sofía Rojas')).toBeInTheDocument();
    await userEvent.click(within(usersSection).getByLabelText('Repartidor'));
    await userEvent.click(within(usersSection).getByRole('button', { name: 'Guardar roles' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'PUT' && call.url === '/auth/admin/users/55/roles',
        ),
      ).toBe(true),
    );

    // 2. Restaurants: create & assign owner
    const restaurantsSection = screen.getByRole('region', { name: 'Restaurantes' });
    expect(await within(restaurantsSection).findByText('La Maestranza Burgers')).toBeInTheDocument();

    await userEvent.type(within(restaurantsSection).getByLabelText('Nombre del local'), 'Sushi Norte');
    await userEvent.type(within(restaurantsSection).getByLabelText('Dirección del local'), 'Av. Kennedy 500');
    await userEvent.click(within(restaurantsSection).getByRole('button', { name: 'Crear local' }));

    expect(await within(restaurantsSection).findByText('Sushi Norte')).toBeInTheDocument();

    await userEvent.type(within(restaurantsSection).getByLabelText('ID de dueño para La Maestranza Burgers'), '55');
    await userEvent.click(within(restaurantsSection).getByRole('button', { name: 'Asignar dueño a La Maestranza Burgers' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'PUT' && call.url === '/api/catalog/admin/restaurants/1/owner',
        ),
      ).toBe(true),
    );

    // 3. Courier onboarding
    const couriersSection = screen.getByRole('region', { name: 'Alta de repartidores' });
    await userEvent.type(within(couriersSection).getByLabelText('ID de usuario repartidor'), '55');
    await userEvent.selectOptions(within(couriersSection).getByLabelText('Vehículo'), 'AUTO');
    await userEvent.click(within(couriersSection).getByRole('button', { name: 'Crear perfil de repartidor' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'POST' && call.url === '/api/delivery/admin/persons',
        ),
      ).toBe(true),
    );

    // 4. Delivery tariff
    const tariffSection = screen.getByRole('region', { name: 'Tarifa de despacho' });
    await within(tariffSection).findByDisplayValue('1500');
    await userEvent.click(within(tariffSection).getByRole('button', { name: 'Guardar tarifa' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'PUT' && call.url === '/api/admin/delivery-tariff',
        ),
      ).toBe(true),
    );

    // 5. Order policy
    const policySection = screen.getByRole('region', { name: 'Política de plazos' });
    const waitInput = await within(policySection).findByLabelText('Espera de repartidor (min)');
    await userEvent.clear(waitInput);
    await userEvent.type(waitInput, '25');
    await userEvent.click(within(policySection).getByRole('button', { name: 'Guardar plazos' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'PUT' && call.url === '/api/admin/order-policy',
        ),
      ).toBe(true),
    );
  });
});

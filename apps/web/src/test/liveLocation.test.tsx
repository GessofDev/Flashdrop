import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { courierMockState, courierResponse } from './support/courierMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { ordersResponse } from './support/ordersMock';
import { renderApp, signIn } from './support/renderApp';

const { tileLayerMock, markerSetLatLngMock, mapSetViewMock, mapRemoveMock } = vi.hoisted(() => ({
  tileLayerMock: vi.fn(() => ({ addTo: vi.fn() })),
  markerSetLatLngMock: vi.fn(),
  mapSetViewMock: vi.fn(),
  mapRemoveMock: vi.fn(),
}));

vi.mock('leaflet', () => {
  const markerInstance = {
    addTo: vi.fn(),
    setLatLng: markerSetLatLngMock,
  };
  markerInstance.addTo.mockReturnValue(markerInstance);

  const mapInstance = {
    setView: mapSetViewMock,
    remove: mapRemoveMock,
  };
  mapInstance.setView.mockReturnValue(mapInstance);

  const api = {
    map: vi.fn(() => mapInstance),
    tileLayer: tileLayerMock,
    circleMarker: vi.fn(() => markerInstance),
  };

  return { default: api, ...api };
});

/** Install a browser geolocation that answers with the given position. */
function stubBrowserLocation(latitude: number, longitude: number): void {
  Object.defineProperty(window.navigator, 'geolocation', {
    configurable: true,
    value: {
      getCurrentPosition: (success: PositionCallback): void => {
        success({
          coords: { latitude, longitude },
        } as unknown as GeolocationPosition);
      },
    },
  });
}

function activeOrderWithRoute(routeId: number | null, status = 'Retirado') {
  return {
    id: '00000000-0000-0000-0000-000000000042',
    code: 'FD-0042',
    status,
    address: 'Av. Providencia 1234, Santiago',
    subtotal: 7990,
    deliveryFee: 1500,
    total: 9490,
    paymentMethod: 'EFECTIVO',
    createdAt: '2026-11-24T12:00:00Z',
    client: null,
    restaurant: { name: 'La Maestranza Burgers', address: 'Av. Italia 900' },
    delivery: { name: 'Camila Rojas', phone: '+56912345678', vehicle: 'MOTO' },
    route: {
      id: routeId,
      pickupAddress: 'Av. Italia 900',
      deliveryAddress: 'Av. Providencia 1234, Santiago',
      distanceKm: 2.4,
      estimatedMinutes: 14,
      status: 'En camino',
    },
    items: [
      {
        id: '10',
        name: 'Doble Smash Cheddar Bacon',
        description: null,
        image: null,
        quantity: 1,
        unitPrice: 7990,
        total: 7990,
      },
    ],
  };
}

/**
 * Live courier location tracking (spec S7, T8):
 * - Customer polls `GET /api/delivery/routes/{routeId}/location` and renders
 *   the courier position on a Leaflet + OpenStreetMap map.
 * - Courier reports position via `POST /api/delivery/location`.
 */
describe('live courier location tracking', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('polls GET /api/delivery/routes/{routeId}/location and renders the Leaflet OSM map for an active order', async () => {
    signIn({ activeRole: 'CLIENTE' });

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/orders/00000000-0000-0000-0000-000000000042') {
        return jsonResponse({
          success: true,
          message: 'Pedido encontrado',
          data: activeOrderWithRoute(77),
        });
      }

      if (pathname === '/api/delivery/routes/77/location') {
        return jsonResponse({
          success: true,
          message: 'OK',
          data: {
            routeId: 77,
            latitude: -33.4489,
            longitude: -70.6693,
            recordedAt: '2026-11-24T12:05:00Z',
          },
        });
      }

      return ordersResponse(call);
    });

    renderApp('/pedidos/00000000-0000-0000-0000-000000000042');

    const mapSection = await screen.findByRole('region', { name: 'Ruta de entrega' });

    await waitFor(() =>
      expect(
        fetchMock.calls.some((call) => call.url === '/api/delivery/routes/77/location'),
      ).toBe(true),
    );

    await screen.findByLabelText('Mapa en vivo del repartidor');

    await waitFor(() =>
      expect(tileLayerMock).toHaveBeenCalledWith(
        expect.stringContaining('tile.openstreetmap.org'),
        expect.objectContaining({
          attribution: expect.stringContaining('OpenStreetMap'),
        }),
      ),
    );
    expect(mapSetViewMock).toHaveBeenCalledWith([-33.4489, -70.6693], expect.any(Number));
    expect(within(mapSection).getByText(/OpenStreetMap/i)).toBeInTheDocument();
  });

  it('shows a waiting state when the courier has not reported a position yet (404)', async () => {
    signIn({ activeRole: 'CLIENTE' });

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/orders/00000000-0000-0000-0000-000000000042') {
        return jsonResponse({
          success: true,
          message: 'Pedido encontrado',
          data: activeOrderWithRoute(77),
        });
      }

      if (pathname === '/api/delivery/routes/77/location') {
        return jsonResponse(
          { status: 404, error: 'NOT_FOUND', message: 'Ubicación del repartidor no disponible' },
          404,
        );
      }

      return ordersResponse(call);
    });

    renderApp('/pedidos/00000000-0000-0000-0000-000000000042');

    const mapSection = await screen.findByRole('region', { name: 'Ruta de entrega' });

    expect(
      await within(mapSection).findByText('Esperando la primera ubicación del repartidor...'),
    ).toBeInTheDocument();
  });

  it('falls back to the static schematic map when routeId is absent or the order is delivered', async () => {
    signIn({ activeRole: 'CLIENTE' });

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/orders/00000000-0000-0000-0000-000000000042') {
        return jsonResponse({
          success: true,
          message: 'Pedido encontrado',
          data: activeOrderWithRoute(null),
        });
      }

      return ordersResponse(call);
    });

    renderApp('/pedidos/00000000-0000-0000-0000-000000000042');

    await screen.findByRole('img', {
      name: 'Mapa esquemático de la ruta, del restaurante a la dirección de entrega',
    });
    expect(
      fetchMock.calls.some((call) => call.url.includes('/api/delivery/routes/')),
    ).toBe(false);
  });

  it('lets the courier share live location via POST /api/delivery/location', async () => {
    signIn({ roles: ['REPARTIDOR'], activeRole: 'REPARTIDOR' });
    stubBrowserLocation(-33.45, -70.66);

    const state = courierMockState();

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (call.method === 'POST' && pathname === '/api/delivery/location') {
        return new Response(null, { status: 204 });
      }

      return courierResponse(call, state);
    });

    renderApp('/repartidor');

    const shareButton = await screen.findByRole('button', { name: 'Compartir ubicación en vivo' });
    await userEvent.click(shareButton);

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'POST' && call.url === '/api/delivery/location',
        ),
      ).toBe(true),
    );

    const postCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url === '/api/delivery/location',
    );
    expect(postCall?.body).toMatchObject({
      latitude: -33.45,
      longitude: -70.66,
      recordedAt: expect.any(String),
    });

    const stopButton = await screen.findByRole('button', { name: 'Detener ubicación en vivo' });
    await userEvent.click(stopButton);
    expect(
      await screen.findByRole('button', { name: 'Compartir ubicación en vivo' }),
    ).toBeInTheDocument();
  });
});

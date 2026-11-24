import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/** Answer of `GET /api/catalog/my/restaurant`: the local of the owner. */
const MY_RESTAURANT = {
  id: 1,
  name: 'La Maestranza Burgers',
  address: 'Av. Providencia 1234, Providencia, Santiago',
  phone: null,
  image: null,
  open: true,
  acceptingOrders: true,
  hours: [],
  latitude: -33.4501,
  longitude: -70.6693,
  deliveryRadiusKm: 5,
};

/**
 * Answer the endpoints of the local of the owner.
 *
 * The service answers with the bare DTO, so the answer of a save is the
 * local with the values the client sent.
 */
function storeResponder(call: RecordedCall): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (call.method === 'PUT' && pathname === '/api/catalog/my/restaurant/location') {
    const body = call.body as { latitude: number; longitude: number };

    return jsonResponse({
      ...MY_RESTAURANT,
      latitude: body.latitude,
      longitude: body.longitude,
    });
  }

  if (call.method === 'PUT' && pathname === '/api/catalog/my/restaurant/delivery-radius') {
    const body = call.body as { deliveryRadiusKm: number };

    return jsonResponse({ ...MY_RESTAURANT, deliveryRadiusKm: body.deliveryRadiusKm });
  }

  return jsonResponse(MY_RESTAURANT);
}

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

/** Answer of the geocoder: the first point of the address searched. */
const GEOCODE_ANSWER = jsonResponse([{ lat: '-33.45', lon: '-70.66' }]);

describe('store location and delivery radius', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    signIn({ activeRole: 'RESTAURANTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the location and the radius of the local that the backend answered', async () => {
    mockFetch(() => jsonResponse(MY_RESTAURANT));
    renderApp('/tienda/ubicacion');

    expect(await screen.findByLabelText('Latitud')).toHaveValue(-33.4501);
    expect(screen.getByLabelText('Longitud')).toHaveValue(-70.6693);
    expect(screen.getByLabelText('Radio de despacho (km)')).toHaveValue(5);

    expect(screen.getByText('-33.4501')).toBeInTheDocument();
    expect(screen.getByText('-70.6693')).toBeInTheDocument();
    expect(screen.getByText('5 km')).toBeInTheDocument();
  });

  it('saves the location with the coordinates that were typed', async () => {
    const fetchMock = mockFetch(storeResponder);
    renderApp('/tienda/ubicacion');

    fireEvent.change(await screen.findByLabelText('Latitud'), { target: { value: '-33.44' } });
    fireEvent.change(screen.getByLabelText('Longitud'), { target: { value: '-70.66' } });

    await userEvent.click(screen.getByRole('button', { name: 'Guardar ubicación' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'PUT' && call.url === '/api/catalog/my/restaurant/location',
        ),
      ).toBe(true),
    );

    const put = fetchMock.calls.find(
      (call) => call.method === 'PUT' && call.url === '/api/catalog/my/restaurant/location',
    );

    expect(put?.body).toEqual({ latitude: -33.44, longitude: -70.66 });
  });

  it('saves the radius with the value that was typed', async () => {
    const fetchMock = mockFetch(storeResponder);
    renderApp('/tienda/ubicacion');

    fireEvent.change(await screen.findByLabelText('Radio de despacho (km)'), {
      target: { value: '12.5' },
    });

    await userEvent.click(screen.getByRole('button', { name: 'Guardar radio' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) =>
            call.method === 'PUT' &&
            call.url === '/api/catalog/my/restaurant/delivery-radius',
        ),
      ).toBe(true),
    );

    const put = fetchMock.calls.find(
      (call) =>
        call.method === 'PUT' && call.url === '/api/catalog/my/restaurant/delivery-radius',
    );

    expect(put?.body).toEqual({ deliveryRadiusKm: 12.5 });
  });

  it('shows the literal message of the backend when a save fails', async () => {
    mockFetch((call) =>
      call.method === 'PUT' &&
      call.url === '/api/catalog/my/restaurant/delivery-radius'
        ? jsonResponse(
            {
              status: 400,
              error: 'BAD_REQUEST',
              message: 'El radio debe estar entre 1 y 50 km, con hasta dos decimales',
            },
            400,
          )
        : jsonResponse(MY_RESTAURANT),
    );
    renderApp('/tienda/ubicacion');

    fireEvent.change(await screen.findByLabelText('Radio de despacho (km)'), {
      target: { value: '80' },
    });

    await userEvent.click(screen.getByRole('button', { name: 'Guardar radio' }));

    expect(
      await screen.findByText('El radio debe estar entre 1 y 50 km, con hasta dos decimales'),
    ).toBeInTheDocument();
  });

  it('fills the coordinates with the position of the browser', async () => {
    stubBrowserLocation(-33.44, -70.66);
    mockFetch(() => jsonResponse(MY_RESTAURANT));
    renderApp('/tienda/ubicacion');

    await userEvent.click(await screen.findByRole('button', { name: 'Usar mi ubicación' }));

    await waitFor(() => expect(screen.getByLabelText('Latitud')).toHaveValue(-33.44));
    expect(screen.getByLabelText('Longitud')).toHaveValue(-70.66);
  });

  it('fills the coordinates with the address of the store', async () => {
    mockFetch((call) =>
      call.url.startsWith('https://nominatim.openstreetmap.org')
        ? GEOCODE_ANSWER
        : jsonResponse(MY_RESTAURANT),
    );
    renderApp('/tienda/ubicacion');

    await userEvent.click(await screen.findByRole('button', { name: 'Buscar dirección' }));

    await waitFor(() => expect(screen.getByLabelText('Latitud')).toHaveValue(-33.45));
    expect(screen.getByLabelText('Longitud')).toHaveValue(-70.66);
  });

  it('has an entry in the navigation of the store and it opens the screen', async () => {
    mockFetch(() => jsonResponse(MY_RESTAURANT));
    renderApp('/tienda/horario');

    const link = await screen.findByRole('link', { name: 'Ubicación & Despacho' });

    await userEvent.click(link);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Ubicación y despacho' }),
    ).toBeInTheDocument();
  });
});

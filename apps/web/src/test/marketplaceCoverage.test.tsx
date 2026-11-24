import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { catalogResponse, RESTAURANTS } from './support/catalogMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

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

/** Leave the browser without the geolocation API. */
function removeBrowserLocation(): void {
  Reflect.deleteProperty(window.navigator, 'geolocation');
}

/** Answer of the geocoder for the address typed in the coverage bar. */
function geocodeAnswer(): Response {
  return jsonResponse([
    {
      lat: '-33.45',
      lon: '-70.66',
      display_name: 'Av. Providencia 1234, Providencia, Santiago',
    },
  ]);
}

/**
 * Marketplace coverage filter (spec S10, T7): sends latitude and longitude to
 * `GET /catalog/restaurants` whenever the customer resolves coordinates,
 * explains when no store covers the zone, and lets the customer view all.
 */
describe('marketplace delivery coverage filter', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends latitude and longitude when the customer shares browser location', async () => {
    stubBrowserLocation(-33.44, -70.66);

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname, searchParams } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/catalog/restaurants' && searchParams.has('latitude')) {
        return jsonResponse([RESTAURANTS[0]]);
      }

      return catalogResponse(call);
    });

    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });
    await waitFor(() =>
      expect(within(restaurants).getByText('Sakura Nikkei Fusion')).toBeInTheDocument(),
    );

    const coverageBar = screen.getByRole('region', { name: 'Cobertura en tu zona' });
    await userEvent.click(within(coverageBar).getByRole('button', { name: 'Usar mi ubicación' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) =>
            call.url.includes('/catalog/restaurants') &&
            call.url.includes('latitude=-33.44') &&
            call.url.includes('longitude=-70.66'),
        ),
      ).toBe(true),
    );

    await waitFor(() =>
      expect(within(restaurants).queryByText('Sakura Nikkei Fusion')).toBeNull(),
    );
    expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument();
  });

  it('geocodes a typed address and filters restaurants by those coordinates', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname, searchParams } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/search') {
        return geocodeAnswer();
      }

      if (pathname === '/catalog/restaurants' && searchParams.has('latitude')) {
        return jsonResponse([RESTAURANTS[1]]);
      }

      return catalogResponse(call);
    });

    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });
    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );

    const coverageBar = screen.getByRole('region', { name: 'Cobertura en tu zona' });
    await userEvent.type(
      within(coverageBar).getByLabelText('Dirección para filtrar cobertura'),
      'Av. Providencia 1234, Santiago',
    );
    await userEvent.click(within(coverageBar).getByRole('button', { name: 'Buscar dirección' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) =>
            call.url.includes('/catalog/restaurants') &&
            call.url.includes('latitude=-33.45') &&
            call.url.includes('longitude=-70.66'),
        ),
      ).toBe(true),
    );

    await waitFor(() =>
      expect(within(restaurants).queryByText('La Maestranza Burgers')).toBeNull(),
    );
    expect(within(restaurants).getByText('Sakura Nikkei Fusion')).toBeInTheDocument();
  });

  it('explains when no restaurant covers the zone and lets the customer view all', async () => {
    stubBrowserLocation(-34.1, -70.7);

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname, searchParams } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/catalog/restaurants' && searchParams.has('latitude')) {
        return jsonResponse([]);
      }

      return catalogResponse(call);
    });

    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });
    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );

    const coverageBar = screen.getByRole('region', { name: 'Cobertura en tu zona' });
    await userEvent.click(within(coverageBar).getByRole('button', { name: 'Usar mi ubicación' }));

    await waitFor(() =>
      expect(
        within(restaurants).getByText('No hay locales con cobertura en tu zona'),
      ).toBeInTheDocument(),
    );

    await userEvent.click(
      within(restaurants).getByRole('button', { name: 'Ver todos los locales' }),
    );

    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );
    expect(within(restaurants).getByText('Sakura Nikkei Fusion')).toBeInTheDocument();

    const lastRestaurantCall = [...fetchMock.calls]
      .reverse()
      .find((call) => call.url.startsWith('/catalog/restaurants'));
    expect(lastRestaurantCall?.url).toBe('/catalog/restaurants');

    await userEvent.click(
      within(coverageBar).getByRole('button', { name: 'Filtrar por mi zona' }),
    );

    await waitFor(() =>
      expect(
        within(restaurants).getByText('No hay locales con cobertura en tu zona'),
      ).toBeInTheDocument(),
    );
  });

  it('shows a clear message and keeps the catalog visible when browser location is unavailable', async () => {
    removeBrowserLocation();
    mockFetch(catalogResponse);

    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });
    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );

    const coverageBar = screen.getByRole('region', { name: 'Cobertura en tu zona' });
    await userEvent.click(within(coverageBar).getByRole('button', { name: 'Usar mi ubicación' }));

    expect(
      await within(coverageBar).findByText('Tu navegador no permite obtener tu ubicación.'),
    ).toBeInTheDocument();
    expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument();
  });
});

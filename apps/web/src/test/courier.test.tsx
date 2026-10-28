import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { courierMockState, courierResponse } from './support/courierMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Courier console (spec S6, S7, S13 and S17).
 *
 * The screens are built from the mockup
 * `consola_del_repartidor_flashdrop_courier`, reduced to the facts the current
 * backend exposes: an available FIFO queue, atomic claims, active routes,
 * completed deliveries and the two order transitions of the courier.
 */
describe('courier console', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'REPARTIDOR' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the available orders in the order returned by the backend', async () => {
    mockFetch((call: RecordedCall) => courierResponse(call, courierMockState()));
    renderApp('/repartidor/pedidos');

    const queue = await screen.findByRole('list', { name: 'Pedidos disponibles' });
    const cards = within(queue).getAllByRole('article');

    expect(cards.map((card) => card.getAttribute('aria-label'))).toEqual([
      'Pedido FD-50',
      'Pedido FD-49',
      'Pedido FD-51',
      'Pedido FD-52',
    ]);

    const first = within(queue).getByRole('article', { name: 'Pedido FD-50' });

    expect(within(first).getByText('La Maestranza Burgers')).toBeInTheDocument();
    expect(within(first).getByText('El Bosque Norte 230, Las Condes')).toBeInTheDocument();
    expect(within(first).getByText('Matías Valenzuela')).toBeInTheDocument();
    expect(within(first).getByText('2,2 km')).toBeInTheDocument();
    expect(within(first).getByText('24 min')).toBeInTheDocument();
    expect(within(first).getByText('$3.200')).toBeInTheDocument();
  });

  it('sends at most three selected order ids when claiming', async () => {
    const state = courierMockState();
    const fetchMock = mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    await screen.findByRole('list', { name: 'Pedidos disponibles' });

    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-50' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-49' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-51' }));

    expect(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-52' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Tomar 3 pedidos' }));

    await waitFor(() => {
      const claim = fetchMock.calls.find(
        (call) => call.method === 'POST' && call.url === '/api/delivery/claim',
      );

      expect(claim?.body).toEqual({ orderIds: [501, 502, 503] });
    });
  });

  it('reloads the available queue after a claim without marking an order locally', async () => {
    const state = courierMockState();
    const recordedCalls = mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    await screen.findByRole('list', { name: 'Pedidos disponibles' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-50' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tomar 1 pedido' }));

    await waitFor(() => {
      const availableCalls = recordedCalls.calls.filter(
        (call) => call.method === 'GET' && call.url.startsWith('/api/orders/available-for-delivery'),
      );

      expect(availableCalls).toHaveLength(2);
      expect(state.availableCalls).toBe(2);
    });

    const card = screen.getByRole('article', { name: 'Pedido FD-50' });

    expect(within(card).queryByText(/tomado|tomado por ti|reclamado/i)).toBeNull();
  });

  it('shows the literal backend message and reloads the queue when a claim is rejected', async () => {
    const state = courierMockState();

    state.claimResponse = () =>
      jsonResponse(
        {
          status: 409,
          error: 'CONFLICT',
          message: 'El pedido ya fue tomado por otro repartidor',
        },
        409,
      );

    mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    await screen.findByRole('list', { name: 'Pedidos disponibles' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-50' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tomar 1 pedido' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El pedido ya fue tomado por otro repartidor',
    );
    await waitFor(() => expect(state.availableCalls).toBe(2));
  });

  it('offers courier profile creation when the backend says the profile is missing', async () => {
    const state = courierMockState();

    state.claimResponse = () =>
      jsonResponse(
        {
          status: 403,
          error: 'FORBIDDEN',
          message: 'El usuario no tiene perfil de repartidor',
        },
        403,
      );

    const fetchMock = mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    await screen.findByRole('list', { name: 'Pedidos disponibles' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-50' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tomar 1 pedido' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El usuario no tiene perfil de repartidor',
    );
    expect(screen.getByRole('button', { name: 'Crear perfil de repartidor' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Crear perfil de repartidor' }));

    await waitFor(() => {
      const profile = fetchMock.calls.find(
        (call) => call.method === 'POST' && call.url === '/api/delivery/persons',
      );

      expect(profile?.body).toEqual({ vehicle: 'MOTO' });
    });
  });

  it('offers pickup and delivery transitions and sends the Spanish order labels', async () => {
    const state = courierMockState();
    const fetchMock = mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor');

    const activeRoutes = await screen.findByRole('region', { name: 'Rutas activas' });
    const route = within(activeRoutes).getByRole('article', { name: 'Ruta FD-50' });

    await userEvent.click(within(route).getByRole('button', { name: 'Confirmar retiro' }));

    await waitFor(() => {
      const updates = fetchMock.calls.filter(
        (call) => call.method === 'PUT' && call.url === '/api/orders/501/status',
      );

      expect(updates).toHaveLength(1);
      expect(updates[0]?.body).toEqual({ status: 'Retirado' });
    });

    const updatedRoute = await screen.findByRole('article', { name: 'Ruta FD-50' });

    await userEvent.click(
      within(updatedRoute).getByRole('button', { name: 'Confirmar entrega' }),
    );

    await waitFor(() => {
      const updates = fetchMock.calls.filter(
        (call) => call.method === 'PUT' && call.url === '/api/orders/501/status',
      );

      expect(updates).toHaveLength(2);
      expect(updates[1]?.body).toEqual({ status: 'Entregado' });
    });
  });

  it('shows no delivery action to a session without the courier permission', async () => {
    signIn({ activeRole: 'REPARTIDOR', roles: ['CLIENTE'] });
    mockFetch((call: RecordedCall) => courierResponse(call, courierMockState()));
    renderApp('/repartidor');

    await screen.findByRole('region', { name: 'Rutas activas' });

    expect(screen.queryByRole('button', { name: /Confirmar (retiro|entrega)/ })).toBeNull();
  });

  it('never renders a PIN, chat, live GPS, tip or rating', async () => {
    mockFetch((call: RecordedCall) => courierResponse(call, courierMockState()));
    const { unmount } = renderApp('/repartidor');

    await screen.findByRole('region', { name: 'Rutas activas' });

    let mainText = screen.getByRole('main').textContent ?? '';

    expect(mainText).not.toMatch(/\bpin\b/i);
    expect(mainText).not.toMatch(/chat/i);
    expect(mainText).not.toMatch(/gps/i);
    expect(mainText).not.toMatch(/propina/i);
    expect(mainText).not.toMatch(/calificaci[oó]n|rating|★/i);
    expect(mainText).not.toMatch(/km\/h|bater[ií]a|sos|auto-aceptar/i);

    unmount();
    renderApp('/repartidor/pedidos');

    await screen.findByRole('list', { name: 'Pedidos disponibles' });

    mainText = screen.getByRole('main').textContent ?? '';

    expect(mainText).not.toMatch(/\bpin\b/i);
    expect(mainText).not.toMatch(/chat/i);
    expect(mainText).not.toMatch(/gps/i);
    expect(mainText).not.toMatch(/propina/i);
    expect(mainText).not.toMatch(/calificaci[oó]n|rating|★/i);
    expect(mainText).not.toMatch(/km\/h|bater[ií]a|sos|auto-aceptar/i);
  });

  it('shows the loading state while the available queue is loading', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/catalog/restaurants') {
        return jsonResponse([
          {
            id: 1,
            name: 'La Maestranza Burgers',
            address: 'Av. Providencia 1234',
            phone: null,
            image: null,
          },
        ]);
      }

      return new Promise<Response>(() => undefined);
    });
    renderApp('/repartidor/pedidos');

    expect(
      await screen.findByRole('status', { name: 'Cargando pedidos disponibles' }),
    ).toBeInTheDocument();
  });

  it('shows the empty state when the backend has no available orders', async () => {
    const state = courierMockState();

    state.availableOrders = [];

    mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    expect(await screen.findByText('No hay pedidos disponibles')).toBeInTheDocument();
  });

  it('shows the literal backend message when the available queue fails', async () => {
    const state = courierMockState();

    state.availableResponse = () =>
      jsonResponse(
        {
          status: 403,
          error: 'FORBIDDEN',
          message: 'Solo los repartidores pueden ver pedidos disponibles',
        },
        403,
      );

    mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Solo los repartidores pueden ver pedidos disponibles',
    );
  });

  it('separates completed deliveries from active routes', async () => {
    mockFetch((call: RecordedCall) => courierResponse(call, courierMockState()));
    renderApp('/repartidor/historial');

    const history = await screen.findByRole('list', { name: 'Entregas completadas' });

    expect(within(history).getByRole('article', { name: 'Entrega FD-48' })).toBeInTheDocument();
    expect(within(history).queryByRole('article', { name: 'Entrega FD-50' })).toBeNull();
  });
});

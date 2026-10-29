import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { KitchenBoardPage } from '@/components/kitchen';
import { SessionProvider, sessionStore } from '@/session';

import { stubAudioContext } from './support/audioMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import {
  KITCHEN_ORDERS,
  KITCHEN_PENDING_ID,
  KITCHEN_PREPARING_ID,
  kitchenMockState,
  kitchenResponse,
} from './support/kitchenMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Kitchen display board of the store (spec S6 and S12).
 *
 * The board is built from the mockup
 * `monitor_de_cocina_y_gestor_de_pedidos_mi_tienda`, with the order machine of
 * the backend: three columns because the restaurant only sets two statuses,
 * the elapsed time measured from `createdAt`, the chime of a new order and
 * the keyboard shortcut that advances a card.
 */
const KDS_PATH = '/tienda/kds';

/** Render the board alone, without the guard of role of the routes. */
function renderBoard(): void {
  render(
    <SessionProvider>
      <MemoryRouter>
        <KitchenBoardPage />
      </MemoryRouter>
    </SessionProvider>,
  );
}

describe('kitchen display board', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'RESTAURANTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('reloads the board when the kitchen asks for it', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    const listCalls = (): number =>
      fetchMock.calls.filter((call) => call.url.startsWith('/api/orders?')).length;

    const before = listCalls();

    await userEvent.click(screen.getByRole('button', { name: 'Actualizar' }));

    await waitFor(() => expect(listCalls()).toBe(before + 1));
  });

  it('sorts the orders into the three columns by the status the backend sent', async () => {
    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });
    const preparing = await screen.findByRole('region', { name: 'Columna EN COCINA' });
    const ready = await screen.findByRole('region', { name: 'Columna LISTO / RETIRO' });

    expect(within(pending).getByText('#FD-46')).toBeInTheDocument();
    expect(within(preparing).getByText('#FD-42')).toBeInTheDocument();
    expect(within(ready).getByText('#FD-40')).toBeInTheDocument();

    expect(within(pending).queryByText('#FD-42')).toBeNull();
    expect(within(preparing).queryByText('#FD-46')).toBeNull();
    expect(within(ready).queryByText('#FD-46')).toBeNull();

    // The order already left the kitchen: it belongs to no column.
    expect(screen.queryByText('#FD-39')).toBeNull();
  });

  it('asks the listing for the view of the restaurant', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    const listCall = fetchMock.calls.find(
      (call) => call.url.startsWith('/api/orders') && !call.url.startsWith('/api/orders/'),
    );

    expect(listCall?.url).toContain('view=restaurant');
  });

  it('offers "Preparando" from "Nuevo pedido" and sends the label of the backend', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(
      within(pending).getByRole('button', { name: 'Avanzar a Preparando' }),
    );

    await waitFor(() => {
      const update = fetchMock.calls.find((call) => call.method === 'PUT');

      expect(update?.url).toBe(`/api/orders/${KITCHEN_PENDING_ID}/status`);
      expect(update?.body).toEqual({ status: 'Preparando' });
    });
  });

  it('offers "Listo para retiro" from "Preparando"', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const preparing = await screen.findByRole('region', { name: 'Columna EN COCINA' });

    await userEvent.click(
      within(preparing).getByRole('button', { name: 'Avanzar a Listo para retiro' }),
    );

    await waitFor(() => {
      const update = fetchMock.calls.find((call) => call.method === 'PUT');

      expect(update?.url).toBe(`/api/orders/${KITCHEN_PREPARING_ID}/status`);
      expect(update?.body).toEqual({ status: 'Listo para retiro' });
    });
  });

  it('offers no transition from "Listo para retiro"', async () => {
    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const ready = await screen.findByRole('region', { name: 'Columna LISTO / RETIRO' });

    expect(within(ready).queryByRole('button', { name: /Avanzar a/ })).toBeNull();
  });

  it('shows no status action to a role that cannot set one', async () => {
    signIn({ activeRole: 'CLIENTE' });
    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderBoard();

    await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    expect(screen.queryAllByRole('button', { name: /Avanzar a/ })).toHaveLength(0);
  });

  it('shows the literal message of the backend when the status change is rejected', async () => {
    const state = kitchenMockState();

    state.statusResponse = () =>
      jsonResponse(
        {
          status: 403,
          error: 'FORBIDDEN',
          message: 'No puedes modificar pedidos de otro restaurante',
        },
        403,
      );

    mockFetch((call: RecordedCall) => kitchenResponse(call, state));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(
      within(pending).getByRole('button', { name: 'Avanzar a Preparando' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No puedes modificar pedidos de otro restaurante',
    );
  });

  it('measures the elapsed time from the creation instant of the order', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-28T22:27:00Z'));

    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });
    const card = within(pending).getByRole('article', { name: 'Pedido FD-46' });

    expect(within(card).getByText('12:00')).toBeInTheDocument();

    const preparing = screen.getByRole('region', { name: 'Columna EN COCINA' });
    const preparingCard = within(preparing).getByRole('article', { name: 'Pedido FD-42' });

    expect(within(preparingCard).getByText('7:00')).toBeInTheDocument();
  });

  it('shows the hour the order arrived next to the elapsed time', async () => {
    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });
    const card = within(pending).getByRole('article', { name: 'Pedido FD-46' });

    expect(within(card).getByText('Recibido 19:15')).toBeInTheDocument();
  });

  it('confirms the change of status with a message of its own', async () => {
    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(
      within(pending).getByRole('button', { name: 'Avanzar a Preparando' }),
    );

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Pedido FD-46 movido a Preparando',
    );
  });

  it('never renders a POS printer, an electronic receipt, a pickup PIN, a kitchen SLA or the legacy "En camino" state', async () => {
    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    const rendered = document.body.textContent ?? '';

    expect(rendered).not.toMatch(/impresora/i);
    expect(rendered).not.toMatch(/\bpos\b/i);
    expect(rendered).not.toMatch(/boleta/i);
    expect(rendered).not.toMatch(/\bpin\b/i);
    expect(rendered).not.toMatch(/c[oó]digo de verificaci/i);
    expect(rendered).not.toMatch(/\bsla\b/i);
    expect(rendered).not.toMatch(/meta\s*:/i);
    expect(rendered).not.toMatch(/en camino/i);
    expect(rendered).not.toMatch(/chat/i);
    expect(rendered).not.toMatch(/mapa/i);
    expect(rendered).not.toMatch(/★/);
  });

  it('reads the dishes of every card with its own call and shows them with the quantity', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });
    const card = within(pending).getByRole('article', { name: 'Pedido FD-46' });

    expect(await within(card).findByText('Doble Smash Burger')).toBeInTheDocument();
    expect(within(card).getByText('2x')).toBeInTheDocument();
    expect(within(card).getByText('Papas Rústicas Trufadas')).toBeInTheDocument();
    expect(within(card).getByText('1x')).toBeInTheDocument();

    const detailCalls = fetchMock.calls.filter(
      (call) => call.method === 'GET' && call.url.startsWith('/api/orders/'),
    );

    expect(detailCalls.length).toBeGreaterThan(0);
  });

  it('opens the detail of the order with its dishes, its address and the totals of the backend', async () => {
    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(
      within(pending).getByRole('button', { name: 'Ver detalle del pedido FD-46' }),
    );

    const drawer = await screen.findByRole('dialog', { name: 'Detalle del pedido FD-46' });

    expect(await within(drawer).findByText('2x Doble Smash Burger')).toBeInTheDocument();
    expect(
      within(drawer).getByText('Av. Pocuro 1930, Depto 402, Providencia, Santiago'),
    ).toBeInTheDocument();
    expect(within(drawer).getByText('$18.470')).toBeInTheDocument();
    expect(within(drawer).getByText('$1.990')).toBeInTheDocument();
    expect(within(drawer).getByText('$20.460')).toBeInTheDocument();
  });

  it('shows the literal message of the backend when the detail cannot be read', async () => {
    const state = kitchenMockState();

    state.details = {
      ...state.details,
      [KITCHEN_PENDING_ID]: undefined,
    };

    mockFetch((call: RecordedCall) => kitchenResponse(call, state));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(
      within(pending).getByRole('button', { name: 'Ver detalle del pedido FD-46' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('Pedido no encontrado');
  });

  it('advances the selected order with the space key', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(within(pending).getByRole('article', { name: 'Pedido FD-46' }));
    await userEvent.keyboard(' ');

    await waitFor(() => {
      const update = fetchMock.calls.find((call) => call.method === 'PUT');

      expect(update?.body).toEqual({ status: 'Preparando' });
    });
  });

  it('ignores the keyboard shortcut while a field has the focus', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(within(pending).getByRole('article', { name: 'Pedido FD-46' }));

    const search = screen.getByRole('searchbox', { name: 'Buscar pedido' });

    await userEvent.click(search);
    await userEvent.keyboard(' ');

    expect(fetchMock.calls.some((call) => call.method === 'PUT')).toBe(false);
  });

  it('rings the kitchen chime when a new order enters the board', async () => {
    const audio = stubAudioContext();

    mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await waitFor(() => expect(audio.starts()).toBeGreaterThan(0));
  });

  it('does not ring the chime when the kitchen silenced it', async () => {
    const audio = stubAudioContext();
    const state = kitchenMockState();

    state.orders = KITCHEN_ORDERS.filter((order) => order.status !== 'Nuevo pedido');

    mockFetch((call: RecordedCall) => kitchenResponse(call, state));
    renderApp(KDS_PATH);

    const preparing = await screen.findByRole('region', { name: 'Columna EN COCINA' });

    await userEvent.click(screen.getByRole('button', { name: 'Chime KDS: Activo' }));

    // A new order enters the board while the chime is silenced.
    state.orders = KITCHEN_ORDERS;

    await userEvent.click(
      within(preparing).getByRole('button', { name: 'Avanzar a Listo para retiro' }),
    );

    expect(await screen.findByText('#FD-46')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Chime KDS: Silenciado' })).toBeInTheDocument();
    expect(audio.starts()).toBe(0);
  });

  it('shows the literal message of the backend when the listing fails', async () => {
    mockFetch(() =>
      jsonResponse(
        {
          status: 403,
          error: 'FORBIDDEN',
          message: 'No tienes permiso para esta vista de pedidos',
        },
        403,
      ),
    );
    renderApp(KDS_PATH);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No tienes permiso para esta vista de pedidos',
    );
  });

  it('shows the three empty columns when the restaurant has no order', async () => {
    const state = kitchenMockState();

    state.orders = [];

    mockFetch((call: RecordedCall) => kitchenResponse(call, state));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    expect(within(pending).queryByRole('article')).toBeNull();
    expect(screen.getAllByText('Sin pedidos')).toHaveLength(3);
  });

  it('keeps the card in the column of the status the backend answers with', async () => {
    const state = kitchenMockState();

    state.statusResponse = () =>
      jsonResponse({ success: true, message: 'Estado actualizado', data: null });

    mockFetch((call: RecordedCall) => kitchenResponse(call, state));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(
      within(pending).getByRole('button', { name: 'Avanzar a Preparando' }),
    );

    const preparing = await screen.findByRole('region', { name: 'Columna EN COCINA' });

    await waitFor(() => expect(within(preparing).getByText('#FD-46')).toBeInTheDocument());
    expect(within(pending).queryByText('#FD-46')).toBeNull();

    const moved = within(preparing).getByRole('article', { name: 'Pedido FD-46' });

    expect(
      within(moved).getByRole('button', { name: 'Avanzar a Listo para retiro' }),
    ).toBeInTheDocument();
  });

  it('shows the card of the order the drawer opened with the same detail', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const pending = await screen.findByRole('region', { name: 'Columna POR ACEPTAR' });

    await userEvent.click(
      within(pending).getByRole('button', { name: 'Ver detalle del pedido FD-46' }),
    );

    await screen.findByRole('dialog', { name: 'Detalle del pedido FD-46' });

    const detailCalls = fetchMock.calls.filter(
      (call) => call.method === 'GET' && call.url.startsWith('/api/orders/'),
    );

    expect(detailCalls).toHaveLength(new Set(detailCalls.map((call) => call.url)).size);
  });

  it('keeps the ready order out of the reach of the keyboard shortcut', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => kitchenResponse(call, kitchenMockState()));
    renderApp(KDS_PATH);

    const ready = await screen.findByRole('region', { name: 'Columna LISTO / RETIRO' });

    await userEvent.click(within(ready).getByRole('article', { name: 'Pedido FD-40' }));
    await userEvent.keyboard(' ');

    expect(fetchMock.calls.some((call) => call.method === 'PUT')).toBe(false);
  });
});

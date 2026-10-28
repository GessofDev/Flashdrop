import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import {
  ORDER_DETAIL,
  ORDER_ID,
  orderDetailResponse,
  orderListResponse,
  ordersResponse,
} from './support/ordersMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Orders of the customer (spec S6 and S10): the list of "Mis pedidos" and the
 * live tracking of one order.
 *
 * The screens are built from the mockup
 * `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0`, with the stepper of
 * the real order machine: five steps and no "En camino" among them, because
 * that status is legacy and no role can set it.
 */
describe('customer orders', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('list of orders', () => {
    it('renders the orders of the customer with the shape of the list endpoint', async () => {
      mockFetch(ordersResponse);
      renderApp('/pedidos');

      const list = await screen.findByRole('list', { name: 'Pedidos del cliente' });

      expect(within(list).getByText('#FD-42')).toBeInTheDocument();
      expect(within(list).getByText('La Maestranza Burgers')).toBeInTheDocument();
      expect(within(list).getByText('Preparando')).toBeInTheDocument();
      expect(within(list).getByText('$13.970')).toBeInTheDocument();
      expect(within(list).getByText('28 oct 2026, 19:15')).toBeInTheDocument();

      expect(within(list).getByText('#FD-41')).toBeInTheDocument();
      expect(within(list).getByText('Sakura Nikkei Fusion')).toBeInTheDocument();
      expect(within(list).getByText('Entregado')).toBeInTheDocument();
      expect(within(list).getByText('$12.970')).toBeInTheDocument();
    });

    it('takes the identity from the token and never sends it in the query', async () => {
      const fetchMock = mockFetch(ordersResponse);
      renderApp('/pedidos');

      await screen.findByRole('list', { name: 'Pedidos del cliente' });

      const listCall = fetchMock.calls.find(
        (call) => call.url.startsWith('/api/orders') && !call.url.startsWith('/api/orders/'),
      );

      expect(listCall?.url).toContain('view=client');
      expect(listCall?.url).not.toContain('user_id');
    });

    it('opens the tracking of the order from the list', async () => {
      mockFetch(ordersResponse);
      renderApp('/pedidos');

      const list = await screen.findByRole('list', { name: 'Pedidos del cliente' });

      expect(within(list).getAllByRole('link', { name: 'Ver seguimiento' })[0]).toHaveAttribute(
        'href',
        `/pedidos/${ORDER_ID}`,
      );
    });

    it('shows the skeleton while the orders arrive', async () => {
      mockFetch(() => new Promise<Response>(() => undefined));
      renderApp('/pedidos');

      expect(await screen.findByRole('status', { name: 'Cargando pedidos' })).toBeInTheDocument();
    });

    it('shows the empty state when the customer has no orders', async () => {
      mockFetch(() => orderListResponse([]));
      renderApp('/pedidos');

      await waitFor(() =>
        expect(screen.getByText('Todavía no tienes pedidos')).toBeInTheDocument(),
      );
    });

    it('shows the literal message of the backend when the list fails', async () => {
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
      renderApp('/pedidos');

      await waitFor(() =>
        expect(screen.getByRole('alert')).toHaveTextContent(
          'No tienes permiso para esta vista de pedidos',
        ),
      );
    });

    it('shows a notice when the listing does not answer with a list', async () => {
      mockFetch(() =>
        jsonResponse({ success: true, message: 'Pedidos encontrados', data: { id: 'FD-42' } }),
      );
      renderApp('/pedidos');

      await waitFor(() =>
        expect(screen.getByRole('alert')).toHaveTextContent(
          'El backend devolvió una respuesta con un formato inesperado.',
        ),
      );
    });
  });

  describe('tracking of one order', () => {
    it('renders the stepper with the five real steps and none of them is "En camino"', async () => {
      mockFetch(ordersResponse);
      renderApp(`/pedidos/${ORDER_ID}`);

      const stepper = await screen.findByRole('list', { name: 'Estado del pedido' });

      expect(within(stepper).getAllByRole('listitem')).toHaveLength(5);

      const labels = within(stepper).getAllByText(
        /^(Confirmado|En cocina|Listo para retiro|Retirado|Entregado)$/,
      );

      expect(labels.map((label) => label.textContent)).toEqual([
        'Confirmado',
        'En cocina',
        'Listo para retiro',
        'Retirado',
        'Entregado',
      ]);
      expect(within(stepper).queryByText(/en camino/i)).toBeNull();
    });

    it('completes the steps the order reached when its status is "Retirado"', async () => {
      mockFetch(ordersResponse);
      renderApp(`/pedidos/${ORDER_ID}`);

      const stepper = await screen.findByRole('list', { name: 'Estado del pedido' });

      expect(within(stepper).getAllByText('Completado')).toHaveLength(4);
      expect(within(stepper).getAllByText('Pendiente')).toHaveLength(1);
      expect(within(stepper).getByText('Pendiente').parentElement).toHaveTextContent('Entregado');
    });

    it('shows the phase of the delivery from the status of the route, not as a step of the order', async () => {
      mockFetch(ordersResponse);
      renderApp(`/pedidos/${ORDER_ID}`);

      const stepper = await screen.findByRole('list', { name: 'Estado del pedido' });

      expect(within(stepper).queryByText(/en camino/i)).toBeNull();
      expect(screen.getByText('En camino')).toBeInTheDocument();
      expect(screen.getByText('Estado de la ruta')).toBeInTheDocument();
    });

    it('shows the subtotal, the delivery fee and the total of the backend', async () => {
      // The total of the backend does not match the sum of the lines on
      // purpose: the summary has to show the number the service sent, never a
      // calculation of the client.
      mockFetch((call: RecordedCall) =>
        call.url.startsWith('/api/orders/')
          ? orderDetailResponse({ ...ORDER_DETAIL, total: 12990 })
          : orderListResponse(),
      );
      renderApp(`/pedidos/${ORDER_ID}`);

      const summary = await screen.findByRole('region', { name: 'Resumen del pedido' });

      expect(within(summary).getByText('$12.980')).toBeInTheDocument();
      expect(within(summary).getByText('$990')).toBeInTheDocument();
      expect(within(summary).getByText('$12.990')).toBeInTheDocument();
      expect(within(summary).queryByText('$13.970')).toBeNull();
    });

    it('shows the restaurant, the address, the payment method and the dishes of the order', async () => {
      mockFetch(ordersResponse);
      renderApp(`/pedidos/${ORDER_ID}`);

      const summary = await screen.findByRole('region', { name: 'Resumen del pedido' });

      expect(within(summary).getByText('La Maestranza Burgers')).toBeInTheDocument();
      expect(
        within(summary).getByText('Av. Pedro de Valdivia 1420, Depto 402, Providencia, Santiago'),
      ).toBeInTheDocument();
      expect(within(summary).getByText('Efectivo')).toBeInTheDocument();
      expect(within(summary).getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();
      expect(within(summary).getAllByText('1x')).toHaveLength(3);
    });

    it('shows the courier with the name and the vehicle the detail sends', async () => {
      mockFetch(ordersResponse);
      renderApp(`/pedidos/${ORDER_ID}`);

      const courier = await screen.findByRole('region', { name: 'Repartidor' });

      expect(within(courier).getByText('Carlos Morales')).toBeInTheDocument();
      expect(within(courier).getByText('Moto Honda CB125F')).toBeInTheDocument();
    });

    it('traces the milestones the data of the order supports', async () => {
      mockFetch(ordersResponse);
      renderApp(`/pedidos/${ORDER_ID}`);

      const timeline = await screen.findByRole('list', { name: 'Bitácora del pedido' });

      expect(within(timeline).getByText('Pedido recibido')).toBeInTheDocument();
      expect(within(timeline).getByText('28 oct 2026, 19:15')).toBeInTheDocument();
      expect(within(timeline).getByText('En cocina')).toBeInTheDocument();
      expect(within(timeline).getByText('Listo para retiro')).toBeInTheDocument();
      expect(within(timeline).getByText('Retirado')).toBeInTheDocument();
      expect(within(timeline).getByText('En ruta')).toBeInTheDocument();
    });

    it('does not trace the milestones the data does not support', async () => {
      mockFetch((call: RecordedCall) =>
        call.url.startsWith('/api/orders/')
          ? orderDetailResponse({
              ...ORDER_DETAIL,
              status: 'Nuevo pedido',
              route: null,
              delivery: null,
            })
          : orderListResponse(),
      );
      renderApp(`/pedidos/${ORDER_ID}`);

      const timeline = await screen.findByRole('list', { name: 'Bitácora del pedido' });

      expect(within(timeline).getByText('Pedido recibido')).toBeInTheDocument();
      expect(within(timeline).queryByText('En cocina')).toBeNull();
      expect(within(timeline).queryByText('Listo para retiro')).toBeNull();
      expect(within(timeline).queryByText('Retirado')).toBeNull();
      expect(within(timeline).queryByText('En ruta')).toBeNull();
    });

    it('shows no map and no courier when the order has no route and no delivery', async () => {
      mockFetch((call: RecordedCall) =>
        call.url.startsWith('/api/orders/')
          ? orderDetailResponse({ ...ORDER_DETAIL, route: null, delivery: null })
          : orderListResponse(),
      );
      renderApp(`/pedidos/${ORDER_ID}`);

      await screen.findByRole('region', { name: 'Resumen del pedido' });

      expect(screen.queryByRole('img', { name: /mapa/i })).toBeNull();
      expect(screen.queryByRole('region', { name: 'Repartidor' })).toBeNull();
      expect(screen.queryByText('Carlos Morales')).toBeNull();
    });

    it('never renders chat, delivery PIN, live positions, ratings or an electronic receipt', async () => {
      mockFetch(ordersResponse);
      renderApp(`/pedidos/${ORDER_ID}`);

      await screen.findByRole('region', { name: 'Resumen del pedido' });

      const rendered = document.body.textContent ?? '';

      expect(rendered).not.toMatch(/chat/i);
      expect(rendered).not.toMatch(/pin de entrega/i);
      expect(rendered).not.toMatch(/km\/h/i);
      expect(rendered).not.toMatch(/\bETA\b/);
      expect(rendered).not.toMatch(/★/);
      expect(rendered).not.toMatch(/4[.,]9\d/);
      expect(rendered).not.toMatch(/boleta/i);
      expect(rendered).not.toMatch(/propina/i);
      expect(rendered).not.toMatch(/cup[oó]n/i);
    });

    it('shows the literal message of the backend when the order cannot be read', async () => {
      mockFetch(() =>
        jsonResponse(
          { status: 403, error: 'FORBIDDEN', message: 'No puedes consultar este pedido' },
          403,
        ),
      );
      renderApp(`/pedidos/${ORDER_ID}`);

      await waitFor(() =>
        expect(screen.getByRole('alert')).toHaveTextContent('No puedes consultar este pedido'),
      );
    });

    it('sends a role of another surface to its home when it opens the tracking of an order', () => {
      signIn({ activeRole: 'RESTAURANTE' });
      renderApp(`/pedidos/${ORDER_ID}`);

      expect(screen.getByTestId('location').textContent).toBe('/tienda/kds');
    });
  });
});

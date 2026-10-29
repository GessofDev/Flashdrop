import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { SalesSummaryResponse } from '@/api';
import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const ANALYTICS_PATH = '/tienda/analiticas';

const SAMPLE_SUMMARY: SalesSummaryResponse = {
  restaurantId: 1,
  range: 'week',
  from: '2026-10-21T00:00:00Z',
  to: '2026-10-28T23:59:59Z',
  totalOrders: 42,
  totalRevenue: 546000,
  averageTicket: 13000,
  topProducts: [
    {
      productId: '550e8400-e29b-41d4-a716-446655440001',
      productName: 'Hamburguesa Doble Smash',
      quantitySold: 28,
      revenue: 277200,
    },
    {
      productId: '550e8400-e29b-41d4-a716-446655440002',
      productName: 'Papas Rústicas Trufadas',
      quantitySold: 22,
      revenue: 98780,
    },
    {
      productId: '550e8400-e29b-41d4-a716-446655440003',
      productName: 'Bebida 350ml',
      quantitySold: 18,
      revenue: 36000,
    },
  ],
};

function salesSummaryResponse(call: RecordedCall, summary: SalesSummaryResponse = SAMPLE_SUMMARY): Response {
  const { pathname, searchParams } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/api/catalog/my/products') {
    return jsonResponse([
      {
        id: 10,
        categoryId: 1,
        restaurantId: 1,
        name: 'Hamburguesa Doble Smash',
        description: 'Doble carne',
        price: 9900,
        available: true,
        image: null,
      },
    ]);
  }

  if (pathname === '/api/orders/restaurants/1/sales-summary') {
    const range = searchParams.get('range') ?? 'week';
    return jsonResponse({
      success: true,
      data: {
        ...summary,
        range,
      },
    });
  }

  return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
}

describe('store sales analytics (spec S14 and S17)', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'RESTAURANTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('renders the 4 canonical KPIs and top products table with backend response', async () => {
    mockFetch((call) => salesSummaryResponse(call));
    renderApp(ANALYTICS_PATH);

    expect(await screen.findByRole('heading', { level: 1, name: 'Analíticas de ventas' })).toBeInTheDocument();

    const ordersKpi = await screen.findByRole('region', { name: 'Métrica de pedidos' });
    expect(within(ordersKpi).getByText('42')).toBeInTheDocument();

    const revenueKpi = screen.getByRole('region', { name: 'Métrica de ingresos' });
    expect(within(revenueKpi).getByText('$546.000')).toBeInTheDocument();

    const ticketKpi = screen.getByRole('region', { name: 'Métrica de ticket promedio' });
    expect(within(ticketKpi).getByText('$13.000')).toBeInTheDocument();

    const topSection = screen.getByRole('region', { name: 'Productos más vendidos' });
    const rows = within(topSection).getAllByRole('row');
    expect(rows).toHaveLength(4); // 1 header + 3 data rows

    expect(within(topSection).getByText('Hamburguesa Doble Smash')).toBeInTheDocument();
    expect(within(topSection).getByText('28 un.')).toBeInTheDocument();
    expect(within(topSection).getByText('$277.200')).toBeInTheDocument();

    expect(within(topSection).getByText('Papas Rústicas Trufadas')).toBeInTheDocument();
    expect(within(topSection).getByText('22 un.')).toBeInTheDocument();
    expect(within(topSection).getByText('$98.780')).toBeInTheDocument();
  });

  it('switches between day, week and month ranges and calls the backend with range parameter', async () => {
    const calls: RecordedCall[] = [];
    mockFetch((call) => {
      calls.push(call);
      return salesSummaryResponse(call);
    });

    renderApp(ANALYTICS_PATH);

    await screen.findByRole('heading', { level: 1, name: 'Analíticas de ventas' });

    const dayButton = screen.getByRole('button', { name: 'Día' });
    const monthButton = screen.getByRole('button', { name: 'Mes' });

    await userEvent.click(dayButton);

    await waitFor(() => {
      const summaryCall = calls.find(
        (c) => c.url.includes('/api/orders/restaurants/1/sales-summary') && c.url.includes('range=day'),
      );
      expect(summaryCall).toBeDefined();
    });

    await userEvent.click(monthButton);

    await waitFor(() => {
      const summaryCall = calls.find(
        (c) => c.url.includes('/api/orders/restaurants/1/sales-summary') && c.url.includes('range=month'),
      );
      expect(summaryCall).toBeDefined();
    });
  });

  it('renders the literal backend message when an invalid range is requested', async () => {
    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products') {
        return jsonResponse([{ id: 10, restaurantId: 1, name: 'Burger', price: 1000, available: true }]);
      }

      if (pathname.includes('/sales-summary')) {
        return jsonResponse(
          {
            success: false,
            message: 'Rango invalido: year. Valores validos: day, week, month',
            status: 400,
          },
          400,
        );
      }

      return jsonResponse({ status: 404 }, 404);
    });

    renderApp(ANALYTICS_PATH);

    expect(
      await screen.findByText('Rango invalido: year. Valores validos: day, week, month'),
    ).toBeInTheDocument();
  });

  it('renders 403 error message when the user lacks permission or does not own the restaurant', async () => {
    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products') {
        return jsonResponse([{ id: 10, restaurantId: 99, name: 'Burger', price: 1000, available: true }]);
      }

      if (pathname.includes('/sales-summary')) {
        return jsonResponse(
          {
            success: false,
            message: 'No puedes consultar las ventas de otro restaurante',
            status: 403,
          },
          403,
        );
      }

      return jsonResponse({ status: 404 }, 404);
    });

    renderApp(ANALYTICS_PATH);

    expect(
      await screen.findByText('No puedes consultar las ventas de otro restaurante'),
    ).toBeInTheDocument();
  });

  it('never renders unsupported features per S14 and S17', async () => {
    mockFetch((call) => salesSummaryResponse(call));
    renderApp(ANALYTICS_PATH);

    await screen.findByRole('heading', { level: 1, name: 'Analíticas de ventas' });

    const mainText = screen.getByRole('main').textContent ?? '';

    expect(mainText).not.toMatch(/margen/i);
    expect(mainText).not.toMatch(/curva\s*horaria|serie\s*diaria/i);
    expect(mainText).not.toMatch(/liquidaci[oó]n|banco|cuenta\s*bancaria/i);
    expect(mainText).not.toMatch(/canales\s*de\s*pago|webpay|transbank/i);
    expect(mainText).not.toMatch(/sugerencia\s*de\s*ia|inteligencia\s*artificial/i);
    expect(mainText).not.toMatch(/alerta.*stock/i);
    expect(mainText).not.toMatch(/satisfacci[oó]n|calificaci[oó]n|rating|★/i);
    expect(mainText).not.toMatch(/sla.*cocina/i);
  });

  it('renders empty state when there are zero orders in the period', async () => {
    const emptySummary: SalesSummaryResponse = {
      restaurantId: 1,
      range: 'week',
      from: '2026-10-21T00:00:00Z',
      to: '2026-10-28T23:59:59Z',
      totalOrders: 0,
      totalRevenue: 0,
      averageTicket: 0,
      topProducts: [],
    };

    mockFetch((call) => salesSummaryResponse(call, emptySummary));
    renderApp(ANALYTICS_PATH);

    await screen.findByRole('heading', { level: 1, name: 'Analíticas de ventas' });

    expect(await screen.findByText('No hay ventas registradas en este período')).toBeInTheDocument();
    const ordersKpi = screen.getByRole('region', { name: 'Métrica de pedidos' });
    expect(within(ordersKpi).getByText('0')).toBeInTheDocument();
  });

  it('renders loading skeleton while sales summary is in flight', async () => {
    mockFetch((call) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/api/catalog/my/products') {
        return jsonResponse([{ id: 10, restaurantId: 1, name: 'Burger', price: 1000, available: true }]);
      }

      return new Promise<Response>(() => undefined);
    });

    renderApp(ANALYTICS_PATH);

    expect(
      await screen.findByRole('status', { name: 'Cargando analíticas de ventas' }),
    ).toBeInTheDocument();
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, request } from '@/api';
import { sessionStore } from '@/session/tokenStore';

import { jsonResponse, mockFetch } from './support/fetchMock';

/**
 * The literal messages of the backend (spec S7).
 *
 * The status column is the one the backend really answers with, verified
 * against the exception handlers of the three services:
 *
 * - `orders-service` maps `AccessDeniedException` to 403 and
 *   `ForbiddenOperationException` to 403, but leaves a domain message without
 *   a known keyword at 400 (`Estado no valido: ...`, `El pedido debe
 *   contener ...`) and maps `perfil de repartidor` to 403.
 * - `delivery-service` maps `AccessDeniedException` to 401, which is where
 *   the `No autenticado` message of the 401 comes from.
 * - `catalog-service` maps `IllegalArgumentException` to 400.
 */
const BACKEND_MESSAGES: ReadonlyArray<{ status: number; kind: string; message: string }> = [
  { status: 401, kind: 'unauthorized', message: 'No autenticado' },
  { status: 403, kind: 'forbidden', message: 'No tienes permiso para esta vista de pedidos' },
  { status: 403, kind: 'forbidden', message: 'No puedes consultar este pedido' },
  { status: 403, kind: 'forbidden', message: 'Solo los repartidores pueden ver pedidos disponibles' },
  { status: 403, kind: 'forbidden', message: 'Solo los restaurantes pueden consultar sus ventas' },
  { status: 403, kind: 'forbidden', message: 'No puedes consultar las ventas de otro restaurante' },
  { status: 403, kind: 'forbidden', message: 'El usuario no tiene perfil de repartidor' },
  { status: 404, kind: 'notFound', message: 'Pedido no encontrado' },
  { status: 409, kind: 'conflict', message: 'Transicion de estado no permitida: ENTREGADO -> RETIRADO' },
  { status: 400, kind: 'badRequest', message: 'Estado no valido: Cancelado' },
  { status: 400, kind: 'badRequest', message: 'El pedido debe contener productos de un mismo local' },
  { status: 400, kind: 'badRequest', message: 'El pedido debe contener al menos un producto' },
  { status: 400, kind: 'badRequest', message: 'Debe enviar al menos un campo para actualizar' },
  { status: 400, kind: 'badRequest', message: 'No route found for orderId=42' },
  {
    status: 400,
    kind: 'badRequest',
    message: 'Rango invalido: year. Valores validos: day, week, month',
  },
  { status: 400, kind: 'badRequest', message: 'El Content-Type no coincide con el contenido de la imagen' },
];

describe('backend error taxonomy', () => {
  beforeEach(() => {
    sessionStore.reset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it.each(BACKEND_MESSAGES)(
    'surfaces the backend message "$message" verbatim with status $status',
    async ({ status, kind, message }) => {
      mockFetch(() => jsonResponse({ status, error: 'ERROR', message }, status));

      const error = await request({ path: '/api/orders', responseShape: 'envelope' }).catch(
        (thrown: unknown) => thrown,
      );

      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(status);
      expect((error as ApiError).kind).toBe(kind);
      expect((error as ApiError).message).toBe(message);
    },
  );

  it('reads the message of the orders-service error shape', async () => {
    mockFetch(() =>
      jsonResponse(
        { status: 403, error: 'FORBIDDEN', message: 'No puedes consultar este pedido' },
        403,
      ),
    );

    const error = await request({ path: '/api/orders/FD-0042', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect((error as ApiError).message).toBe('No puedes consultar este pedido');
    expect((error as ApiError).code).toBe('FORBIDDEN');
  });

  it('reads the message of the delivery-service error shape', async () => {
    mockFetch(() =>
      jsonResponse(
        {
          timestamp: '2026-10-28T12:00:00Z',
          status: 401,
          error: 'Unauthorized',
          message: 'No autenticado',
        },
        401,
      ),
    );

    const error = await request({ path: '/api/delivery/routes', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect((error as ApiError).message).toBe('No autenticado');
  });

  it('reads the message of the auth-service error shape', async () => {
    mockFetch(() =>
      jsonResponse(
        {
          code: 'INVALID_CREDENTIALS',
          service: 'auth-service',
          trace_id: 'trace-1',
          message: 'Credenciales invalidas',
        },
        401,
      ),
    );

    const error = await request({
      path: '/auth/login',
      method: 'POST',
      body: {},
      responseShape: 'bare',
      session: 'public',
    }).catch((thrown: unknown) => thrown);

    expect((error as ApiError).message).toBe('Credenciales invalidas');
    expect((error as ApiError).code).toBe('INVALID_CREDENTIALS');
  });

  it('falls back to a client side message when the backend sends none', async () => {
    mockFetch(() => new Response('', { status: 502 }));

    const error = await request({ path: '/api/orders', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).kind).toBe('serverError');
    expect((error as ApiError).message).not.toBe('');
  });
});

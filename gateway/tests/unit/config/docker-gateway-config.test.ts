import path from 'path';
import { describe, it, expect, beforeAll } from 'vitest';
import { loadConfig } from '../../../src/config/loader.js';
import { RouteRegistry } from '../../../src/routing/registry.js';
import type { GatewayConfig, RouteConfig } from '../../../src/config/types.js';

/**
 * Contrato del config que se hornea en la imagen del gateway (docker/Dockerfile ->
 * /app/config/gateway.yaml). Estos asserts evitan que un cambio de prefijo, target
 * o de politica de autenticacion rompa en silencio el flujo de pagos.
 */
describe('Gateway config desplegado (docker/gateway.yaml)', () => {
  let config: GatewayConfig;

  const routeByPrefix = (prefix: string): RouteConfig => {
    const route = config.routes.find((r) => r.prefix === prefix);
    if (!route) {
      throw new Error(`La ruta "${prefix}" no esta declarada en docker/gateway.yaml`);
    }
    return route;
  };

  beforeAll(() => {
    process.env['CORS_ORIGINS'] = '["http://localhost:3000"]';
    process.env['AUTH_SERVICE_JWKS_URI'] =
      'http://auth-service:8081/auth/.well-known/jwks.json';
    config = loadConfig(path.resolve('docker/gateway.yaml')) as GatewayConfig;
  });

  it('declara /api/payments/fake contra orders-service conservando el prefijo', () => {
    const route = routeByPrefix('/api/payments/fake');

    expect(route.target).toBe('http://orders-service:8083');
    expect(route.stripPrefix).toBe(false);
    expect(route.backendName).toBe('orders-service');
  });

  it('no declara el prefijo de pagos como ruta publica', () => {
    const route = routeByPrefix('/api/payments/fake');

    expect(route.jwt?.enabled).not.toBe(false);

    const publicOverride = (config.jwtOverrides ?? []).find((o) =>
      o.path.startsWith('/api/payments'),
    );
    expect(publicOverride).toBeUndefined();
  });

  it('resuelve la pantalla de pago contra la ruta de pagos', () => {
    const registry = new RouteRegistry(config);
    const match = registry.match('/api/payments/fake/tx-123/resolve');

    expect(match).not.toBeNull();
    expect(match?.route.prefix).toBe('/api/payments/fake');
    expect(match?.route.target).toBe('http://orders-service:8083');
    expect(match?.route.stripPrefix).toBe(false);
  });

  it('no captura el webhook de pagos, que no se expone por el gateway', () => {
    const registry = new RouteRegistry(config);
    const match = registry.match('/api/payments/webhook');

    expect(match?.route.prefix).not.toBe('/api/payments/fake');
  });

  it('deja publico POST /auth/email/confirm para el enlace del correo', () => {
    const override = (config.jwtOverrides ?? []).find(
      (o) => o.path === '/auth/email/confirm',
    );

    expect(override).toBeDefined();
    expect(override?.jwt?.enabled).toBe(false);
  });

  it('deja protegido POST /auth/email/change-request', () => {
    const override = (config.jwtOverrides ?? []).find(
      (o) => o.path === '/auth/email/change-request',
    );

    expect(override).toBeUndefined();
  });
});

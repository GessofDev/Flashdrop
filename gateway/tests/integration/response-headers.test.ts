import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import pino from 'pino';
import { buildServer } from '../../src/server.js';
import { MiddlewarePipeline } from '../../src/middleware/pipeline.js';
import type { GatewayConfig } from '../../src/config/types.js';

/**
 * Integration tests for response header filtering.
 *
 * Per RFC 7230 §6.1, hop-by-hop headers (Connection, Keep-Alive,
 * Proxy-Connection, Transfer-Encoding, Upgrade, TE, Trailers) describe
 * the connection to the backend, not the resource. Forwarding them to
 * the client confuses downstream readers — most notoriously
 * `Transfer-Encoding: chunked` on a body that Undici has already
 * de-chunked breaks the read on the client side.
 *
 * Reference: QA report by Felipe, 2026-10-09, "Tema 1 / Punto 2 —
 * Gateway response headers".
 */
describe('Response header filtering (hop-by-hop, RFC 7230 §6.1)', () => {
  /**
   * Mock backend that returns a successful response with all the
   * hop-by-hop headers set. The body is sent via a single `end()` call
   * so Node computes `Content-Length` itself; the hop-by-hop headers
   * are added via `setHeader` so the test exercises the same code path
   * the gateway uses to filter them, without forcing an HTTP protocol
   * violation (e.g. setting both `Content-Length` and
   * `Transfer-Encoding: chunked`).
   */
  class HopByHopBackend {
    private server: http.Server;

    constructor() {
      this.server = http.createServer((_req, res) => {
        res.statusCode = 200;
        res.setHeader('Content-Type', 'application/json');
        // Hop-by-hop headers — the gateway MUST strip these from the
        // response it returns to the client (RFC 7230 §6.1).
        res.setHeader('Transfer-Encoding', 'chunked');
        res.setHeader('Connection', 'close');
        res.setHeader('Keep-Alive', 'timeout=5');
        res.setHeader('Proxy-Connection', 'keep-alive');
        res.setHeader('Upgrade', 'h2c');
        res.setHeader('TE', 'trailers');
        res.setHeader('Trailers', 'X-Custom-Trailer');
        res.end(JSON.stringify({ ok: true }));
      });
    }

    start(): Promise<number> {
      return new Promise((resolve) => {
        this.server.listen(0, '127.0.0.1', () => {
          const address = this.server.address() as AddressInfo;
          resolve(address.port);
        });
      });
    }

    stop(): Promise<void> {
      return new Promise((resolve, reject) => {
        this.server.close((err) => (err ? reject(err) : resolve()));
      });
    }
  }

  let backendPort: number;
  let server: ReturnType<typeof buildServer>;
  let backend: HopByHopBackend;

  beforeAll(async () => {
    backend = new HopByHopBackend();
    backendPort = await backend.start();

    const config: GatewayConfig = {
      server: { port: 3000, host: '127.0.0.1', bodyLimit: 6 * 1024 * 1024 },
      redis: { url: 'redis://localhost:6379' },
      logging: { level: 'error' },
      metrics: { enabled: false, path: '/metrics', defaultLabels: {} },
      routes: [
        {
          prefix: '/api',
          target: `http://127.0.0.1:${backendPort}`,
          stripPrefix: false,
        },
      ],
    };
    const logger = pino({ level: 'silent' });
    const pipeline = new MiddlewarePipeline();
    server = buildServer(config, pipeline, logger);
  });

  afterAll(async () => {
    if (server) {
      await server.close();
    }
    await backend.stop();
  });

  it('strips hop-by-hop headers from the upstream response (RFC 7230 §6.1)', async () => {
    const response = await server.inject({
      method: 'GET',
      url: '/api/whatever',
    });

    expect(response.statusCode).toBe(200);
    // Body must still arrive intact — we filter headers, not the body.
    expect(response.body).toBe('{"ok":true}');

    const headers = response.headers as Record<string, string | string[] | undefined>;

    // Headers the backend set with a unique value MUST NOT leak through.
    // The filter runs before Fastify's serialization, so anything we see here
    // either came from the gateway or was added by Fastify as a default.
    expect(headers['transfer-encoding']).toBeUndefined();
    expect(headers['keep-alive']).toBeUndefined();
    expect(headers['proxy-connection']).toBeUndefined();
    expect(headers['upgrade']).toBeUndefined();
    expect(headers['te']).toBeUndefined();
    expect(headers['trailers']).toBeUndefined();

    // `connection` is special: Fastify's HTTP/1.1 default adds
    // `connection: keep-alive` to the inject response, which is harmless
    // (and what the client should see). What matters is that the
    // backend's specific value (`close`) did not leak through.
    expect(headers['connection']).not.toBe('close');

    // Sanity: end-to-end headers we DO want to forward survive.
    expect(headers['content-type']).toBe('application/json');
  });
});

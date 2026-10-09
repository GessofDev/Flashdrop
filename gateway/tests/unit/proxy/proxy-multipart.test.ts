import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import http from 'node:http';
import pino from 'pino';
import { buildServer } from '../../../src/server.js';
import { MiddlewarePipeline } from '../../../src/middleware/pipeline.js';
import type { GatewayConfig } from '../../../src/config/types.js';

/**
 * Plan de pruebas §2.4 (ProxyMultipartStreamTest): el proxy reenvía el cuerpo multipart sin
 * pasarlo por JSON.stringify, y el backend recibe el Content-Type con su boundary y un
 * Content-Length igual al tamaño real de los bytes enviados. Backend simulado con http.
 */
describe('Proxy multipart passthrough', () => {
  let backend: http.Server;
  let received: { body: Buffer; headers: http.IncomingHttpHeaders };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let gateway: any;

  beforeAll(async () => {
    backend = http.createServer((req, res) => {
      const chunks: Buffer[] = [];
      req.on('data', (c: Buffer) => chunks.push(c));
      req.on('end', () => {
        received = { body: Buffer.concat(chunks), headers: req.headers };
        res.writeHead(201, { 'content-type': 'application/json' });
        res.end('{"ok":true}');
      });
    });
    await new Promise<void>((resolve) => backend.listen(0, '127.0.0.1', () => resolve()));
    const port = (backend.address() as { port: number }).port;

    const config: GatewayConfig = {
      server: { port: 3000, host: '127.0.0.1', bodyLimit: 6 * 1024 * 1024 },
      redis: { url: 'redis://localhost:6379' },
      logging: { level: 'silent' },
      metrics: { enabled: false, path: '/metrics' },
      routes: [{ prefix: '/catalog', target: `http://127.0.0.1:${port}`, stripPrefix: true }],
    };
    gateway = buildServer(config, new MiddlewarePipeline(), pino({ level: 'silent' }));
  });

  afterAll(async () => {
    await gateway.close();
    await new Promise<void>((resolve) => backend.close(() => resolve()));
  });

  it('el backend recibe los mismos bytes, el Content-Type con su boundary y el Content-Length real', async () => {
    const boundary = '----ProxyPlanBoundary42';
    const payload = Buffer.concat([
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="a.jpg"\r\n`,
      ),
      Buffer.from('Content-Type: image/jpeg\r\n\r\n'),
      Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01]),
      Buffer.from(`\r\n--${boundary}--\r\n`),
    ]);

    const res = await gateway.inject({
      method: 'POST',
      url: '/catalog/api/catalog/my/products/image',
      headers: { 'content-type': `multipart/form-data; boundary=${boundary}` },
      payload,
    });

    expect(res.statusCode).toBe(201);
    expect(received.body.equals(payload)).toBe(true);
    expect(received.headers['content-type']).toBe(`multipart/form-data; boundary=${boundary}`);
    expect(Number(received.headers['content-length'])).toBe(payload.length);
  });
});

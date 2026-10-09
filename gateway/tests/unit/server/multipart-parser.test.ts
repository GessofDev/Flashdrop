import { describe, it, expect } from 'vitest';
import fastify from 'fastify';
import { registerRawBodyParsers } from '../../../src/server/raw-body-parser.js';

/**
 * Plan de pruebas §2.4 (MultipartParserTest): el gateway acepta cuerpos multipart hasta el
 * límite configurado (6 MiB por defecto) y rechaza con 413 los que lo superan, antes de
 * reenviarlos al proxy. El límite se prueba en el borde exacto (límite y límite + 1 byte).
 */
const SIX_MIB = 6 * 1024 * 1024;
const BOUNDARY = '----PlanBoundary';

function buildApp() {
  const app = fastify({ logger: false, bodyLimit: SIX_MIB });
  registerRawBodyParsers(app, SIX_MIB);
  app.post('/upload', async (req, reply) => {
    const body = req.body as Buffer;
    await reply.send({ isBuffer: Buffer.isBuffer(body), size: body.length });
  });
  return app;
}

function post(app: ReturnType<typeof buildApp>, payload: Buffer) {
  return app.inject({
    method: 'POST',
    url: '/upload',
    headers: { 'content-type': `multipart/form-data; boundary=${BOUNDARY}` },
    payload,
  });
}

describe('MultipartParser (límite de 6 MiB)', () => {
  it('acepta un cuerpo multipart pequeño y lo entrega como Buffer', async () => {
    const app = buildApp();
    const res = await post(app, Buffer.alloc(1024, 0xab));

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ isBuffer: true, size: 1024 });
  });

  it('acepta un cuerpo de exactamente 6 MiB', async () => {
    const app = buildApp();
    const res = await post(app, Buffer.alloc(SIX_MIB, 0xab));

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ isBuffer: true, size: SIX_MIB });
  });

  it('rechaza con 413 un cuerpo de 6 MiB + 1 byte', async () => {
    const app = buildApp();
    const res = await post(app, Buffer.alloc(SIX_MIB + 1, 0xab));

    expect(res.statusCode).toBe(413);
  });
});

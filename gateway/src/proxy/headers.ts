import type { FastifyRequest } from 'fastify';
import type { ProxyForwardingHeaders, ProxyRequestBody } from './types.js';

/**
 * Fastify exposes incoming HTTP headers as `string | string[] | undefined`.
 * For single-value headers (most forwarding headers) we want the first
 * element when it's a list, the value when it's a string, or undefined
 * when it's missing. Extracting this here keeps the call sites readable
 * and lets us stop sprinkling nested ternaries across the forwarding logic.
 */
function firstHeaderValue(value: string | string[] | undefined): string | undefined {
  if (value === undefined) {
    return undefined;
  }
  return Array.isArray(value) ? value[0] : value;
}

/**
 * Hop-by-hop response headers that must NOT be forwarded to the client.
 * Per RFC 7230 §6.1, these describe a single transport-level connection
 * rather than the resource and become meaningless (or harmful) when
 * relayed by a proxy. Storing them in a Set lets the response filter run
 * in O(1) per header.
 *
 * `connection`, `keep-alive`, `proxy-connection`, `transfer-encoding`,
 * `upgrade`, `te`, `trailers` — all lowercased; comparison is case-insensitive.
 */
export const HOP_BY_HOP_RESPONSE_HEADERS: ReadonlySet<string> = new Set([
  'connection',
  'keep-alive',
  'proxy-connection',
  'transfer-encoding',
  'upgrade',
  'te',
  'trailers',
]);

/**
 * Computes the exact byte length the proxy should advertise in
 * `content-length` for the outgoing body. The value sent by the client
 * is not authoritative: when the gateway re-serializes a parsed JSON
 * payload (whitespace stripped, numbers normalized), the byte count
 * shrinks and the receiver rejects the request with a "Request body
 * length does not match content-length header" error. Recomputing from
 * the actual outgoing bytes is the only way to keep the framing honest.
 *
 * Returns `null` for streaming bodies (AsyncIterable); the caller should
 * not overwrite `content-length` in that case so that Undici can frame
 * the payload as chunked (or honor a client-provided value when present).
 *
 * @param body The body that will be sent to the backend.
 * @returns Byte count as a number, or `null` when no overwrite is safe.
 */
export function computeContentLength(body: ProxyRequestBody): number | null {
  if (body === null) {
    return null;
  }
  if (typeof body === 'string') {
    return Buffer.byteLength(body, 'utf8');
  }
  if (Buffer.isBuffer(body)) {
    return body.length;
  }
  // AsyncIterable / stream: leave content-length alone.
  return null;
}

/**
 * Construye de manera segura y estándar las cabeceras de reenvío HTTP (forwarding)
 * que informan al servicio de destino (backend) sobre el origen real de la petición.
 *
 * @param request Objeto de petición HTTP de Fastify.
 * @returns Diccionario de cabeceras de reenvío formateadas.
 */
export function buildForwardingHeaders(request: FastifyRequest): ProxyForwardingHeaders {
  const clientIp = request.ip || '127.0.0.1';

  // 1. Calcular X-Forwarded-For acumulativo
  const existingXFF = request.headers['x-forwarded-for'];
  let xForwardedFor: string;

  if (existingXFF) {
    const xffStr = typeof existingXFF === 'string' ? existingXFF : existingXFF.join(', ');
    xForwardedFor = `${xffStr.trim()}, ${clientIp}`;
  } else {
    xForwardedFor = clientIp;
  }

  // 2. Calcular X-Forwarded-Host (El host que solicitó originalmente el cliente)
  const xForwardedHost = request.headers.host || 'localhost';

  // 3. Calcular X-Forwarded-Proto (El esquema original de conexión http / https)
  const xForwardedProto =
    request.protocol || firstHeaderValue(request.headers['x-forwarded-proto']) || 'http';

  // 4. Calcular X-Real-IP (La IP directa conectada al Gateway)
  const xRealIp = clientIp;

  return {
    'x-forwarded-for': xForwardedFor,
    'x-forwarded-host': xForwardedHost,
    'x-forwarded-proto': xForwardedProto,
    'x-real-ip': xRealIp,
  };
}

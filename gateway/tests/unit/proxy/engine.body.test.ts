import { describe, it, expect } from 'vitest';
import { buildRequestBody } from '../../../src/proxy/engine.js';
import { computeContentLength } from '../../../src/proxy/headers.js';

/**
 * Tests for the body-dispatch helper used by ProxyEngine.forward().
 *
 * The function MUST:
 *   - pass `Buffer` through to Undici as Buffer (no JSON.stringify wrapping)
 *   - pass `string` through as-is
 *   - pass `AsyncIterable<unknown>` (streams) through as-is
 *   - JSON.stringify plain objects (parsed JSON bodies) for backward compat
 *   - return `undefined` for GET/HEAD methods regardless of body
 *   - return `undefined` for null/undefined input
 *
 * Initially RED — buildRequestBody is not exported yet. After T4 implementation
 * these must turn GREEN.
 */
describe('buildRequestBody (engine body dispatch)', () => {
  describe('scalar inputs', () => {
    it('returns null (no body) when body is undefined', () => {
      expect(buildRequestBody(undefined, 'POST')).toBeNull();
    });

    it('returns null (no body) when body is null', () => {
      expect(buildRequestBody(null, 'POST')).toBeNull();
    });

    it('returns string as-is (no transformation)', () => {
      const s = 'hello world';
      expect(buildRequestBody(s, 'POST')).toBe(s);
    });
  });

  describe('binary inputs', () => {
    it('returns Buffer as-is (no JSON.stringify wrapping)', () => {
      const buf = Buffer.from([1, 2, 3, 4, 5]);
      const result = buildRequestBody(buf, 'POST');
      expect(result).toBe(buf);
      // Negative assertion: must NOT be the JSON-wrapped form
      expect(result).not.toBe(JSON.stringify(buf));
    });

    it('preserves empty Buffer identity', () => {
      const buf = Buffer.alloc(0);
      expect(buildRequestBody(buf, 'POST')).toBe(buf);
    });

    it('handles large Buffer (passes through reference)', () => {
      const buf = Buffer.alloc(1024 * 1024, 0xab); // 1 MiB
      expect(buildRequestBody(buf, 'POST')).toBe(buf);
    });
  });

  describe('stream inputs', () => {
    it('returns AsyncIterable as-is', async () => {
      async function* gen() {
        yield Buffer.from('chunk-1');
        yield Buffer.from('chunk-2');
      }
      const stream = gen();
      const result = buildRequestBody(stream, 'POST');
      expect(result).toBe(stream);
    });
  });

  describe('object inputs (legacy JSON path)', () => {
    it('JSON.stringifies a plain object', () => {
      const obj = { foo: 'bar', n: 42 };
      expect(buildRequestBody(obj, 'POST')).toBe(JSON.stringify(obj));
    });

    it('JSON.stringifies an array', () => {
      const arr = [1, 2, 3];
      expect(buildRequestBody(arr, 'POST')).toBe(JSON.stringify(arr));
    });

    it('JSON.stringifies nested object', () => {
      const obj = { a: { b: { c: [1, 2] } } };
      expect(buildRequestBody(obj, 'POST')).toBe(JSON.stringify(obj));
    });
  });

  describe('method-based filtering', () => {
    it('returns null (no body) for GET even when body is a Buffer', () => {
      const buf = Buffer.from('x');
      expect(buildRequestBody(buf, 'GET')).toBeNull();
    });

    it('returns null (no body) for HEAD even when body is a string', () => {
      expect(buildRequestBody('x', 'HEAD')).toBeNull();
    });

    it('returns Buffer for POST (not undefined)', () => {
      const buf = Buffer.from('data');
      expect(buildRequestBody(buf, 'POST')).toBe(buf);
    });

    it('returns Buffer for PUT', () => {
      const buf = Buffer.from('data');
      expect(buildRequestBody(buf, 'PUT')).toBe(buf);
    });

    it('returns Buffer for DELETE', () => {
      const buf = Buffer.from('data');
      expect(buildRequestBody(buf, 'DELETE')).toBe(buf);
    });

    it('returns Buffer for PATCH', () => {
      const buf = Buffer.from('data');
      expect(buildRequestBody(buf, 'PATCH')).toBe(buf);
    });
  });
});

/**
 * Tests for `computeContentLength`, the helper that recomputes the
 * `content-length` header from the actual outgoing body. The bug fixed
 * alongside this helper (PR #50, QA report 2026-10-09) was that the
 * gateway copied the client's `content-length` and sent a re-serialized
 * body with a different byte count, producing 502 errors on any
 * non-compact JSON input.
 *
 * Contract:
 *   - string  -> Buffer.byteLength(string, 'utf8')
 *   - Buffer  -> buffer.length
 *   - null    -> null  (caller must not overwrite content-length)
 *   - stream  -> null  (caller must not overwrite content-length)
 */
describe('computeContentLength (outgoing content-length helper)', () => {
  describe('string bodies', () => {
    it('returns Buffer.byteLength of a compact JSON string', () => {
      const s = '{"foo":"bar","n":2500}';
      expect(computeContentLength(s)).toBe(Buffer.byteLength(s, 'utf8'));
    });

    it('counts multibyte UTF-8 characters correctly (ñ is 2 bytes)', () => {
      // '{"x":"ñ"}' is 9 characters but the `ñ` occupies 2 bytes in
      // UTF-8, so the byte length is 10 (8 single-byte chars + 2 bytes
      // for ñ). This matters when a backend normalizes the encoding.
      const s = '{"x":"ñ"}';
      expect(computeContentLength(s)).toBe(10);
      expect(Buffer.byteLength(s, 'utf8')).toBe(10);
    });

    it('returns 0 for an empty string', () => {
      expect(computeContentLength('')).toBe(0);
    });
  });

  describe('buffer bodies', () => {
    it('returns buffer.length for binary payloads', () => {
      const buf = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]);
      expect(computeContentLength(buf)).toBe(6);
    });

    it('returns 0 for an empty buffer', () => {
      expect(computeContentLength(Buffer.alloc(0))).toBe(0);
    });
  });

  describe('stream bodies (no overwrite)', () => {
    it('returns null for an AsyncIterable (caller must not touch content-length)', () => {
      async function* gen() {
        yield Buffer.from('chunk');
      }
      expect(computeContentLength(gen())).toBeNull();
    });
  });

  describe('null bodies (no body)', () => {
    it('returns null for null', () => {
      expect(computeContentLength(null)).toBeNull();
    });
  });

  describe('regression: round-trip shrink', () => {
    it('shrinks when JSON.stringify normalizes a number (2500.0 -> 2500)', () => {
      // The original request was 13 bytes: {"n":2500.0}
      // The outgoing body is 10 bytes: {"n":2500}
      // The gateway must advertise 10, not 13.
      const original = '{"n":2500.0}';
      const reserialized = JSON.stringify(JSON.parse(original));
      expect(reserialized).toBe('{"n":2500}');
      expect(computeContentLength(reserialized)).toBe(10);
    });

    it('shrinks when JSON.stringify strips pretty-print whitespace', () => {
      const pretty = JSON.stringify({ a: 1, b: 2 }, null, 2);
      const compact = JSON.stringify({ a: 1, b: 2 });
      expect(pretty.length).toBeGreaterThan(compact.length);
      expect(computeContentLength(compact)).toBe(compact.length);
    });
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';

import { resolveImageUrl } from '@/api/imageUrl';

/**
 * The catalog answers with a relative path (`/catalog/images/products/...`)
 * and the app prepends its configured backend base URL, so the picture is
 * served by the gateway and not by the static host of the web app.
 */
describe('resolveImageUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('keeps an absolute URL as it is', () => {
    expect(resolveImageUrl('https://cdn.test/pizza.png')).toBe('https://cdn.test/pizza.png');
  });

  it('keeps a data URL as it is', () => {
    expect(resolveImageUrl('data:image/png;base64,AAA')).toBe('data:image/png;base64,AAA');
  });

  it('returns null when there is no picture', () => {
    expect(resolveImageUrl(null)).toBeNull();
    expect(resolveImageUrl(undefined)).toBeNull();
    expect(resolveImageUrl('   ')).toBeNull();
  });

  it('prefixes a relative path with the configured backend base URL', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');

    expect(resolveImageUrl('/catalog/images/products/2026/10/pizza.webp')).toBe(
      'http://api.test/catalog/images/products/2026/10/pizza.webp',
    );
  });

  it('keeps a relative path when no backend base URL is configured', () => {
    expect(resolveImageUrl('/catalog/images/products/2026/10/pizza.webp')).toBe(
      '/catalog/images/products/2026/10/pizza.webp',
    );
  });

  it('joins the base URL and the path without doubling the slash', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test/');

    expect(resolveImageUrl('/catalog/images/pizza.webp')).toBe(
      'http://api.test/catalog/images/pizza.webp',
    );
  });
});

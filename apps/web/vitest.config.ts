import { defineConfig, mergeConfig } from 'vitest/config';

import viteConfig from './vite.config.ts';

/**
 * Vitest configuration.
 *
 * Reuses the Vite config (plugins + `@/` alias) so the test environment and
 * the dev/build environment resolve modules the same way.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      restoreMocks: true,
    },
  }),
);

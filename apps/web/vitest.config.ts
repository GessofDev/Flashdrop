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
      /**
       * Deadline of one test.
       *
       * The tests of the app drive the screens with the real timers of the
       * browser, so a test that types and clicks takes seconds. Vitest kills
       * a test at five seconds by default, and on a machine that runs the
       * whole suite at once a correct test can cross that line and fail for
       * a reason that has nothing to do with the behaviour it checks.
       */
      testTimeout: 30_000,
    },
  }),
);

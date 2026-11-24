import '@testing-library/jest-dom/vitest';

import { configure } from '@testing-library/dom';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

/**
 * Deadline of the async waits of the tests.
 *
 * Testing Library waits one second by default, and the screens of the app
 * answer several calls before they show something: on a machine that runs the
 * whole suite at once the first render can take longer than that and the
 * test fails for a reason that has nothing to do with the behaviour it
 * checks.
 */
configure({ asyncUtilTimeout: 5_000 });

afterEach(() => {
  cleanup();
});

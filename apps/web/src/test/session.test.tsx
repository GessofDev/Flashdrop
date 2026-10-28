import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { SessionProvider, useSession } from '@/session';

function SessionProbe() {
  const { session } = useSession();

  return <p data-testid="session-state">{session === null ? 'no-session' : 'has-session'}</p>;
}

describe('session context', () => {
  it('starts without a session', () => {
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    );

    expect(screen.getByTestId('session-state')).toHaveTextContent('no-session');
  });

  it('fails loudly when the hook is used outside the provider', () => {
    expect(() => render(<SessionProbe />)).toThrow(/SessionProvider/);
  });
});

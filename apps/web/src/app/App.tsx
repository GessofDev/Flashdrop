import { BrowserRouter } from 'react-router-dom';

import { SessionProvider } from '@/session';

import { AppRoutes } from './AppRoutes';
import { SessionRedirectBridge } from './SessionRedirectBridge';

/**
 * Application root: session context plus the router skeleton.
 *
 * The canonical shell (top bar of five zones and the role switcher) is added
 * by task T3; until then each route renders its own placeholder.
 */
export function App() {
  return (
    <SessionProvider>
      <BrowserRouter>
        <SessionRedirectBridge />
        <AppRoutes />
      </BrowserRouter>
    </SessionProvider>
  );
}

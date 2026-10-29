import { BrowserRouter } from 'react-router-dom';

import { CartProvider } from '@/cart';
import { SessionProvider } from '@/session';

import { AppRoutes } from './AppRoutes';
import { SessionRedirectBridge } from './SessionRedirectBridge';

/**
 * Application root: session context, cart of the customer and the router
 * skeleton.
 *
 * The canonical shell (top bar of five zones and the role switcher) is added
 * by the protected layout of the routes.
 */
export function App() {
  return (
    <SessionProvider>
      <CartProvider>
        <BrowserRouter>
          <SessionRedirectBridge />
          <AppRoutes />
        </BrowserRouter>
      </CartProvider>
    </SessionProvider>
  );
}

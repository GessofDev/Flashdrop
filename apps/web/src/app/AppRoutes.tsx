import { Route, Routes } from 'react-router-dom';

import { LoginPage } from '@/components/auth';
import {
  CourierAvailablePage,
  CourierConsolePage,
  CourierHistoryPage,
} from '@/components/courier';
import { KitchenBoardPage } from '@/components/kitchen';
import { MarketplacePage } from '@/components/marketplace';
import { OrderListPage, OrderTrackingPage } from '@/components/orders';
import { ProfilePage } from '@/components/profile';
import { StoreAnalyticsPage, StoreCatalogPage } from '@/components/store';

import { ProtectedLayout } from './ProtectedLayout';
import { RoutePlaceholder } from './RoutePlaceholder';
import { routePaths } from './routePaths';

/**
 * Placeholder per route, with the mockup each screen will be built from.
 *
 * The marketplace and the orders of the customer already have their screen
 * (tasks T5 and T7); the other screens land in the tasks that follow, so this
 * file only wires the navigation skeleton for them.
 */
const ROUTE_PLACEHOLDERS = [
  {
    path: routePaths.storeDispatchHistory,
    title: 'Historial de despachos',
    description: 'Pedidos despachados por la tienda, con su estado final.',
    mockup: 'anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0',
  },
] as const;

/**
 * Routes of the app.
 *
 * The login route is public; every other screen lives inside the protected
 * layout, which holds the guard and the canonical shell.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path={routePaths.login} element={<LoginPage />} />
      <Route element={<ProtectedLayout />}>
        <Route path={routePaths.marketplace} element={<MarketplacePage />} />
        <Route path={routePaths.orders} element={<OrderListPage />} />
        <Route path={routePaths.orderDetail} element={<OrderTrackingPage />} />
        <Route path={routePaths.storeCatalog} element={<StoreCatalogPage />} />
        <Route path={routePaths.storeKds} element={<KitchenBoardPage />} />
        <Route path={routePaths.storeAnalytics} element={<StoreAnalyticsPage />} />
        <Route path={routePaths.courier} element={<CourierConsolePage />} />
        <Route path={routePaths.courierAvailable} element={<CourierAvailablePage />} />
        <Route path={routePaths.courierHistory} element={<CourierHistoryPage />} />
        <Route path={routePaths.profile} element={<ProfilePage />} />
        {ROUTE_PLACEHOLDERS.map((route) => (
          <Route
            key={route.path}
            path={route.path}
            element={
              <RoutePlaceholder
                title={route.title}
                description={route.description}
                mockup={route.mockup}
              />
            }
          />
        ))}
        <Route
          path="*"
          element={
            <RoutePlaceholder
              title="Página no encontrada"
              description="La ruta solicitada no existe en la aplicación."
              mockup="flashdrop_shell_can_nica_gu_a_de_navegaci_n_por_rol"
            />
          }
        />
      </Route>
    </Routes>
  );
}

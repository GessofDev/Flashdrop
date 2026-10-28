import { Route, Routes } from 'react-router-dom';

import { LoginPage } from '@/components/auth';
import { MarketplacePage } from '@/components/marketplace';
import { OrderListPage, OrderTrackingPage } from '@/components/orders';

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
    path: routePaths.storeKds,
    title: 'Monitor de cocina',
    description: 'Tablero de pedidos del restaurante con sus tres columnas.',
    mockup: 'monitor_de_cocina_y_gestor_de_pedidos_mi_tienda',
  },
  {
    path: routePaths.storeCatalog,
    title: 'Catálogo y menú',
    description: 'Gestión del catálogo, precios y disponibilidad de la tienda.',
    mockup: 'gesti_n_de_cat_logo_y_men_mi_tienda',
  },
  {
    path: routePaths.storeDispatchHistory,
    title: 'Historial de despachos',
    description: 'Pedidos despachados por la tienda, con su estado final.',
    mockup: 'anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0',
  },
  {
    path: routePaths.storeAnalytics,
    title: 'Analíticas de ventas',
    description: 'Indicadores de ventas de la tienda y productos más vendidos.',
    mockup: 'anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0',
  },
  {
    path: routePaths.courier,
    title: 'Consola del repartidor',
    description: 'Ruta activa del repartidor y estado de la operación.',
    mockup: 'consola_del_repartidor_flashdrop_courier',
  },
  {
    path: routePaths.courierAvailable,
    title: 'Pedidos disponibles',
    description: 'Cola de pedidos disponibles para el reparto, en orden de llegada.',
    mockup: 'consola_del_repartidor_flashdrop_courier',
  },
  {
    path: routePaths.courierHistory,
    title: 'Historial de entregas',
    description: 'Entregas ya realizadas por el repartidor.',
    mockup: 'consola_del_repartidor_flashdrop_courier',
  },
  {
    path: routePaths.profile,
    title: 'Perfil',
    description: 'Identidad, roles y dispositivos conectados del usuario.',
    mockup: 'perfil_multi_rol_y_centro_de_cuentas_flashdrop_v2.0_1',
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

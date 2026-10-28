import { Route, Routes } from 'react-router-dom';

import { RoutePlaceholder } from './RoutePlaceholder';
import { routePaths } from './routePaths';

/**
 * Placeholder per route, with the mockup each screen will be built from.
 *
 * The screens themselves land in later tasks (T5 to T12); this file only
 * wires the navigation skeleton so the router, the roles and the routes are
 * already in place.
 */
const ROUTE_PLACEHOLDERS = [
  {
    path: routePaths.marketplace,
    title: 'Marketplace',
    description: 'Restaurantes y platos disponibles para el cliente.',
    mockup: 'marketplace_y_cat_logo_de_clientes_flashdrop_v2.0',
  },
  {
    path: routePaths.orders,
    title: 'Mis pedidos',
    description: 'Pedidos del cliente y seguimiento de su estado.',
    mockup: 'seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0',
  },
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
    path: routePaths.storeAnalytics,
    title: 'Analíticas de ventas',
    description: 'Indicadores de ventas de la tienda y productos más vendidos.',
    mockup: 'anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0',
  },
  {
    path: routePaths.courier,
    title: 'Consola del repartidor',
    description: 'Pedidos disponibles, rutas activas e historial de entregas.',
    mockup: 'consola_del_repartidor_flashdrop_courier',
  },
  {
    path: routePaths.profile,
    title: 'Perfil',
    description: 'Identidad, roles y dispositivos conectados del usuario.',
    mockup: 'perfil_multi_rol_y_centro_de_cuentas_flashdrop_v2.0_1',
  },
  {
    path: routePaths.login,
    title: 'Iniciar sesión',
    description: 'Acceso unificado con RUT o email y destino por rol.',
    mockup: 'iniciar_sesi_n_portal_unificado_flashdrop',
  },
] as const;

export function AppRoutes() {
  return (
    <Routes>
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
    </Routes>
  );
}

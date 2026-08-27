-- ============================================================================
-- Seed EXCLUSIVO de orders-service
-- ============================================================================
-- Pobla ÚNICAMENTE las tablas propias de Orders: client, orders, order_items.
-- NO inserta en users/products/restaurant/delivery/delivery_routes (son de otros
-- servicios). Los IDs externos usados (restaurant_id, delivery_id, product_id,
-- user_id) coinciden con los datasets demo de Catalog/Delivery/Auth.
-- Ejecutable sobre BD vacía de Orders. Idempotente (ON CONFLICT DO NOTHING).
-- ============================================================================

INSERT INTO public.client (id, user_id) VALUES
  (1, 1),
  (2, 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.orders (id, client_id, restaurant_id, delivery_id, status, address, subtotal, delivery_fee, total, payment_method) VALUES
  (1,  1, 1, 1, 'Preparando',        'Av. Providencia 1200, Santiago',   19480, 2500, 21980, 'Tarjeta'),
  (2,  1, 2, 1, 'Listo para retiro', 'Los Leones 850, Santiago',         11500, 2200, 13700, 'Efectivo'),
  (3,  2, 1, 2, 'En camino',         'Nueva Costanera 3900, Vitacura',   11990, 3000, 14990, 'Tarjeta'),
  (4,  2, 2, NULL, 'Nuevo pedido',    'Manuel Montt 420, Providencia',    17480, 2500, 19980, 'Transferencia'),
  (5,  1, 1, 1, 'Entregado',         'Santa Isabel 060, Santiago',        8990, 2000, 10990, 'Tarjeta'),
  (6,  1, 2, 1, 'Nuevo pedido',      'Av. Providencia 1200, Santiago',   15990, 2500, 18490, 'Efectivo'),
  (7,  1, 1, 1, 'Nuevo pedido',      'Juan bohon 1622, Santiago',         7490, 2500,  9990, 'Efectivo'),
  (8,  1, 2, 1, 'Nuevo pedido',      'selene 1421, Santiago',            10900, 2500, 13400, 'Efectivo'),
  (9,  1, 1, 1, 'Nuevo pedido',      'selene 1442, Santiago',            11990, 2500, 14490, 'Efectivo'),
  (10, 1, 2, 1, 'Nuevo pedido',      'Av. Providencia 125, Santiago',    10000, 2500, 12500, 'Efectivo')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.order_items (id, order_id, product_id, quantity, unit_price, total) VALUES
  (1,  1,  1,  2, 8990, 17980),
  (2,  1,  6,  1, 1490,  1490),
  (3,  2,  4,  1, 11500, 11500),
  (4,  3,  3,  1, 11990, 11990),
  (5,  4,  5,  1, 10900, 10900),
  (6,  4,  6,  2, 1490,  2980),
  (7,  5,  1,  1, 8990,  8990),
  (8,  6,  7,  1, 15990, 15990),
  (9,  7,  2,  1, 7490,  7490),
  (10, 8,  5,  1, 10900, 10900),
  (11, 9,  3,  1, 11990, 11990),
  (12, 10, 9,  1, 10000, 10000)
ON CONFLICT (id) DO NOTHING;

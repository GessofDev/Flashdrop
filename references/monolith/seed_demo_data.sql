USE flash_drop_delivery;

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(255) NULL,
  image TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_categories_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(255) NULL,
  price DECIMAL(10,2) NOT NULL,
  image TEXT NULL,
  is_available TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_products_category (category_id),
  KEY idx_products_restaurant (restaurant_id),
  CONSTRAINT fk_products_category
    FOREIGN KEY (category_id) REFERENCES categories(id)
    ON DELETE RESTRICT,
  CONSTRAINT fk_products_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_id BIGINT UNSIGNED NOT NULL,
  restaurant_id BIGINT UNSIGNED NOT NULL,
  delivery_id BIGINT UNSIGNED NULL,
  status VARCHAR(60) NOT NULL DEFAULT 'Nuevo pedido',
  address VARCHAR(220) NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
  delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
  total DECIMAL(10,2) NOT NULL DEFAULT 0,
  payment_method VARCHAR(60) NOT NULL DEFAULT 'Efectivo',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_orders_client (client_id),
  KEY idx_orders_restaurant (restaurant_id),
  KEY idx_orders_delivery (delivery_id),
  CONSTRAINT fk_orders_client
    FOREIGN KEY (client_id) REFERENCES client(id)
    ON DELETE RESTRICT,
  CONSTRAINT fk_orders_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurant(id)
    ON DELETE RESTRICT,
  CONSTRAINT fk_orders_delivery
    FOREIGN KEY (delivery_id) REFERENCES delivery(id)
    ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  unit_price DECIMAL(10,2) NOT NULL,
  total DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_items_order (order_id),
  KEY idx_order_items_product (product_id),
  CONSTRAINT fk_order_items_order
    FOREIGN KEY (order_id) REFERENCES orders(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_order_items_product
    FOREIGN KEY (product_id) REFERENCES products(id)
    ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS delivery_routes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  pickup_address VARCHAR(220) NOT NULL,
  delivery_address VARCHAR(220) NOT NULL,
  distance_km DECIMAL(5,2) NOT NULL,
  estimated_minutes INT NOT NULL,
  status VARCHAR(60) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_delivery_routes_order (order_id),
  CONSTRAINT fk_delivery_routes_order
    FOREIGN KEY (order_id) REFERENCES orders(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO categories (id, name, description, image) VALUES
  (1, 'Hamburguesas', 'Combos y sandwiches preparados al momento', 'assets/img/burger1.png'),
  (2, 'Pizzas', 'Pizzas familiares, medianas y porcionadas', 'assets/img/pizza.png'),
  (3, 'Bebidas', 'Bebidas, jugos y aguas', 'assets/img/bag.png'),
  (4, 'Promociones', 'Ofertas destacadas para delivery express', 'assets/img/hamburguesa.png')
ON DUPLICATE KEY UPDATE
  description = VALUES(description),
  image = VALUES(image);

INSERT INTO products (id, category_id, restaurant_id, name, description, price, image, is_available) VALUES
  (1, 1, 1, 'Burger doble', 'Doble carne, cheddar, pepinillos y salsa de la casa', 8990, 'assets/img/burger1.png', 1),
  (2, 1, 1, 'Burger crispy', 'Pollo crispy, lechuga, tomate y mayo ahumada', 7490, 'assets/img/burger2.png', 1),
  (3, 4, 1, 'Combo urbano', 'Burger doble, papas fritas y bebida lata', 11990, 'assets/img/hamburguesa.png', 1),
  (4, 2, 2, 'Pizza pepperoni', 'Pizza familiar con pepperoni y extra queso', 11500, 'assets/img/pizza.png', 1),
  (5, 2, 2, 'Pizza vegetariana', 'Champinones, aceitunas, pimenton y mozzarella', 10900, 'assets/img/pizza2.png', 1),
  (6, 3, 2, 'Bebida lata', 'Bebida individual 350cc', 1490, 'assets/img/bag.png', 1),
  (7, 4, 2, 'Pack familiar pizza', 'Pizza familiar, 2 bebidas y pan de ajo', 15990, 'assets/img/pizza.png', 1)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  restaurant_id = VALUES(restaurant_id),
  name = VALUES(name),
  description = VALUES(description),
  price = VALUES(price),
  image = VALUES(image),
  is_available = VALUES(is_available);

INSERT INTO orders (id, client_id, restaurant_id, delivery_id, status, address, subtotal, delivery_fee, total, payment_method) VALUES
  (1, 1, 1, 1, 'Preparando', 'Av. Providencia 1200, Santiago', 19480, 2500, 21980, 'Tarjeta'),
  (2, 1, 2, 1, 'Listo para retiro', 'Los Leones 850, Santiago', 11500, 2200, 13700, 'Efectivo'),
  (3, 2, 1, 2, 'En camino', 'Nueva Costanera 3900, Vitacura', 11990, 3000, 14990, 'Tarjeta'),
  (4, 2, 2, NULL, 'Nuevo pedido', 'Manuel Montt 420, Providencia', 17480, 2500, 19980, 'Transferencia'),
  (5, 1, 1, 1, 'Entregado', 'Santa Isabel 060, Santiago', 8990, 2000, 10990, 'Tarjeta')
ON DUPLICATE KEY UPDATE
  client_id = VALUES(client_id),
  restaurant_id = VALUES(restaurant_id),
  delivery_id = VALUES(delivery_id),
  status = VALUES(status),
  address = VALUES(address),
  subtotal = VALUES(subtotal),
  delivery_fee = VALUES(delivery_fee),
  total = VALUES(total),
  payment_method = VALUES(payment_method);

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, total) VALUES
  (1, 1, 1, 2, 8990, 17980),
  (2, 1, 6, 1, 1490, 1490),
  (3, 2, 4, 1, 11500, 11500),
  (4, 3, 3, 1, 11990, 11990),
  (5, 4, 5, 1, 10900, 10900),
  (6, 4, 6, 2, 1490, 2980),
  (7, 5, 1, 1, 8990, 8990)
ON DUPLICATE KEY UPDATE
  order_id = VALUES(order_id),
  product_id = VALUES(product_id),
  quantity = VALUES(quantity),
  unit_price = VALUES(unit_price),
  total = VALUES(total);

INSERT INTO delivery_routes (id, order_id, pickup_address, delivery_address, distance_km, estimated_minutes, status) VALUES
  (1, 1, 'Urban Burger Demo, Av. Providencia 1000', 'Av. Providencia 1200, Santiago', 2.40, 18, 'Retirar pedido'),
  (2, 2, 'Flash Restaurant Demo, Los Leones 500', 'Los Leones 850, Santiago', 4.10, 25, 'Listo para retiro'),
  (3, 3, 'Urban Burger Demo, Av. Providencia 1000', 'Nueva Costanera 3900, Vitacura', 6.80, 32, 'En camino'),
  (4, 5, 'Urban Burger Demo, Av. Providencia 1000', 'Santa Isabel 060, Santiago', 1.80, 12, 'Entregado')
ON DUPLICATE KEY UPDATE
  pickup_address = VALUES(pickup_address),
  delivery_address = VALUES(delivery_address),
  distance_km = VALUES(distance_km),
  estimated_minutes = VALUES(estimated_minutes),
  status = VALUES(status);

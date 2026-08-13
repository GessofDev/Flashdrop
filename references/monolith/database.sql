CREATE DATABASE IF NOT EXISTS flash_drop_delivery
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE flash_drop_delivery;

DROP TABLE IF EXISTS user_has_roles;
DROP TABLE IF EXISTS client;
DROP TABLE IF EXISTS delivery;
DROP TABLE IF EXISTS restaurant;
DROP TABLE IF EXISTS login;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email VARCHAR(180) NOT NULL,
  rut VARCHAR(30) NULL,
  name VARCHAR(120) NULL,
  last_name VARCHAR(120) NULL,
  phone VARCHAR(40) NULL,
  photo TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  UNIQUE KEY uq_users_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE login (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  login VARCHAR(180) NOT NULL,
  password VARCHAR(255) NOT NULL,
  id_users BIGINT UNSIGNED NOT NULL,
  status TINYINT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_login_login (login),
  KEY idx_login_user (id_users),
  CONSTRAINT fk_login_user
    FOREIGN KEY (id_users) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE roles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  image TEXT NULL,
  route VARCHAR(180) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_roles_name (name)
) ENGINE=InnoDB;

CREATE TABLE user_has_roles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_user BIGINT UNSIGNED NOT NULL,
  id_rol BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_role (id_user, id_rol),
  KEY idx_uhr_role (id_rol),
  CONSTRAINT fk_uhr_user
    FOREIGN KEY (id_user) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_uhr_role
    FOREIGN KEY (id_rol) REFERENCES roles(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE client (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_date DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_client_user (user_id),
  CONSTRAINT fk_client_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE restaurant (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  address VARCHAR(220) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_restaurant_user (user_id),
  CONSTRAINT fk_restaurant_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE delivery (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  vehicle VARCHAR(80) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_delivery_user (user_id),
  CONSTRAINT fk_delivery_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO roles (id, name, image, route) VALUES
  (1, 'Cliente', '', '/client/products/list'),
  (2, 'Restaurante', '', '/restaurant/orders/list'),
  (3, 'Repartidor', '', '/delivery/orders/list')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  image = VALUES(image),
  route = VALUES(route);

-- Password para todos los usuarios demo: 123456
-- Hash bcrypt generado para pruebas locales.
INSERT INTO users (id, email, rut, name, last_name, phone, photo) VALUES
  (1, 'cliente@demo.cl', '11.111.111-1', 'Cliente', 'Demo', '+56911111111', NULL),
  (2, 'restaurante@demo.cl', '22.222.222-2', 'Restaurante', 'Demo', '+56922222222', NULL),
  (3, 'repartidor@demo.cl', '33.333.333-3', 'Repartidor', 'Demo', '+56933333333', NULL),
  (4, 'admin@demo.cl', '44.444.444-4', 'Admin', 'Multirol', '+56944444444', NULL)
ON DUPLICATE KEY UPDATE
  rut = VALUES(rut),
  name = VALUES(name),
  last_name = VALUES(last_name),
  phone = VALUES(phone),
  photo = VALUES(photo);

INSERT INTO login (login, password, id_users, status) VALUES
  ('cliente@demo.cl', '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 1, 1),
  ('restaurante@demo.cl', '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 2, 1),
  ('repartidor@demo.cl', '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 3, 1),
  ('admin@demo.cl', '$2b$10$K0ZXwndhzN0H5uw6bfxF9eMqGEbiHunGMkv6/U9bE.BGr91d6tYM.', 4, 1)
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  status = VALUES(status);

INSERT INTO client (user_id) VALUES (1), (4)
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

INSERT INTO restaurant (user_id, name, address) VALUES
  (2, 'Urban Burger Demo', 'Av. Providencia 1200'),
  (4, 'Flash Restaurant Demo', 'Los Leones 850')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  address = VALUES(address);

INSERT INTO delivery (user_id, vehicle) VALUES
  (3, 'Moto'),
  (4, 'Auto')
ON DUPLICATE KEY UPDATE vehicle = VALUES(vehicle);

INSERT INTO user_has_roles (id_user, id_rol) VALUES
  (1, 1),
  (2, 2),
  (3, 3),
  (4, 1),
  (4, 2),
  (4, 3)
ON DUPLICATE KEY UPDATE id_user = VALUES(id_user);

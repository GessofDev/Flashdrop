-- V1__create-tables.sql
-- Creates internal.delivery, internal.delivery_routes, internal.delivery_persons

CREATE SCHEMA IF NOT EXISTS internal;

CREATE TABLE internal.delivery (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    address VARCHAR(512) NOT NULL,
    status VARCHAR(32) DEFAULT 'PENDIENTE',
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_delivery_status ON internal.delivery(status);

CREATE TABLE internal.delivery_routes (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL REFERENCES internal.delivery(id) ON DELETE CASCADE,
    order_id VARCHAR(64) NOT NULL,
    delivery_person_id BIGINT,
    status VARCHAR(32) DEFAULT 'ASSIGNED',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_routes_delivery_person ON internal.delivery_routes(delivery_person_id);
CREATE INDEX idx_routes_status ON internal.delivery_routes(status);
CREATE INDEX idx_routes_order_id ON internal.delivery_routes(order_id);

CREATE TABLE internal.delivery_persons (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) UNIQUE NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_delivery_persons_user_id ON internal.delivery_persons(user_id);
CREATE INDEX idx_delivery_persons_active ON internal.delivery_persons(active);

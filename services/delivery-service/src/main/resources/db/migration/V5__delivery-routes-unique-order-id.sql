-- V5__delivery-routes-unique-order-id.sql
-- Closes plan §9.5 D8.
--
-- Per plan D5 + D8 the delivery_routes table must enforce that at most one
-- route exists per orderId. Orders-service (feature/orders-migracion, C-6
-- contract) creates the route via POST /api/internal/routes when an order
-- is created; delivery-service's claim flow must UPDATE that row, not
-- insert a duplicate.
--
-- The UNIQUE(order_id) constraint is the DB-level enforcement for the
-- "already claimed" 409 rule: a concurrent second claim that beats our
-- SELECT FOR UPDATE lock would surface as a UNIQUE violation when the
-- UPSERT touches the column uniqueness key (we keep the lock as the
-- primary path, but the constraint is the safety net — defence in depth).
--
-- The pre-existing non-unique index idx_routes_order_id becomes redundant
-- once the UNIQUE constraint is added (UNIQUE creates its own backing
-- index in PostgreSQL); dropping it avoids maintaining two indexes for
-- the same look-up.

ALTER TABLE internal.delivery_routes
    ADD CONSTRAINT uq_delivery_routes_order_id UNIQUE (order_id);

DROP INDEX IF EXISTS internal.idx_routes_order_id;

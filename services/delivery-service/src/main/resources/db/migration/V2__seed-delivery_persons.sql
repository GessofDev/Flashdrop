-- V2__seed-delivery_persons.sql
-- Idempotent seed for FR-5 + Scenario 1

INSERT INTO internal.delivery_persons (user_id, active)
VALUES ('1', true)
ON CONFLICT (user_id) DO NOTHING;

-- =====================================================================
-- V6 - Corrección del seed de delivery_persons (FR-5 / feedback de integración 2026-10)
--
-- ANTECEDENTE
-- El seed V2 inserta una fila con user_id='1' asumiendo que el usuario 1
-- de auth-service es el repartidor demo. PERO en auth-service (V2__seed_development.sql)
-- los IDs están coordinados así:
--   1 → cliente@demo.cl       (rol: Cliente)
--   2 → restaurante@demo.cl   (rol: Restaurante)
--   3 → repartidor@demo.cl    (rol: Repartidor)
--   4 → admin@demo.cl         (roles: Cliente + Restaurante + Repartidor)
--
-- Por eso el equipo de integración detectó que un cliente podía tomar pedidos:
-- user_id='1' (cliente) tenía perfil de repartidor en delivery-service y el
-- SecurityConfig (hasta WU-3) no exigía el rol, así que el claim pasaba.
-- Además, repartidor@demo.cl (user_id='3') no tenía perfil y recibía
-- DeliveryPersonNotFoundException al intentar tomar pedidos.
--
-- CORRECCIÓN
-- 1. Borrar la fila indebida user_id='1' (cliente no es repartidor).
-- 2. Crear la fila que falta para user_id='3' (repartidor@demo.cl).
-- 3. Crear la fila para user_id='4' (admin@demo.cl es Repartidor también).
--
-- SEGURIDAD
-- - DELETE con WHERE user_id='1' es seguro: solo afecta esa fila por la UNIQUE
--   constraint sobre user_id y la fila existe (insertada por V2).
-- - INSERT con ON CONFLICT DO NOTHING es idempotente: si la fila ya existe
--   (porque el admin corrió la migración a mano), el segundo intento no falla.
-- - No se toca la tabla ni se cambian columnas: solo datos del seed.
--
-- IMPACTO EN AMBIENTES EXISTENTES
-- Esta migración BORRA la fila user_id='1' de delivery_persons. Si en otro
-- ambiente (staging, demo de Felipe) alguien creó un repartidor con
-- user_id='1' además del cliente, esa fila se va a perder. Recomendación:
-- hacer backup de delivery_db antes de aplicar en producción.
-- =====================================================================

DELETE FROM internal.delivery_persons
WHERE user_id = '1';

INSERT INTO internal.delivery_persons (user_id, active)
VALUES ('3', true)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO internal.delivery_persons (user_id, active)
VALUES ('4', true)
ON CONFLICT (user_id) DO NOTHING;

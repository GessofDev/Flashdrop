-- =====================================================================
-- V5 - Perfil de cliente anonimizable
--
-- Al eliminar su cuenta, el vinculo del perfil de cliente con el usuario de Auth
-- (client.user_id, dato personal) se borra y el perfil queda con end_date. El perfil
-- se conserva solo para que los pedidos historicos mantengan su FK (orders.client_id).
-- El UNIQUE se mantiene: varios NULL son validos en PostgreSQL.
-- =====================================================================

ALTER TABLE public.client
    ALTER COLUMN user_id DROP NOT NULL;

-- V8__delivery_person_rating_stats.sql
-- Cache local de los promedios de calificacion que expone reviews-service.
--
-- reviews-service es el dueno del dato (S9) y delivery-service no lee su base
-- (AGENTS.md §2): guarda aca el ultimo agregado conocido para poder servirlo
-- cuando el pull falla.
--
-- La clave es `courier_user_id`: el id del repartidor en auth (auth.users.id),
-- que es el destino que reviews-service usa para target_type=REPARTIDOR. Es
-- el mismo valor que internal.delivery_persons.user_id guarda como texto.
-- `count` es el nombre de la columna que fija el contrato de la tabla.

CREATE TABLE internal.delivery_person_rating_stats (
    courier_user_id BIGINT PRIMARY KEY,
    average DOUBLE PRECISION,
    count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_person_rating_stats_updated_at
    ON internal.delivery_person_rating_stats (updated_at);

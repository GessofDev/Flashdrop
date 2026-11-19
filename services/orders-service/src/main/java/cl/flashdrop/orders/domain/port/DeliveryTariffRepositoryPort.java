package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.DeliveryTariff;

import java.util.UUID;

/** Puerto de salida para la tarifa de envio que define el administrador. */
public interface DeliveryTariffRepositoryPort {

    /** La tarifa vigente; vacia si el administrador no cargo ningun rango. */
    DeliveryTariff find();

    /** Reemplaza ambas tablas de una sola vez. {@code updatedByUserId} deja auditado el cambio. */
    DeliveryTariff replace(DeliveryTariff tariff, UUID updatedByUserId);
}

package com.flashdrop.auth.application.port.outbound;

/**
 * Llamadas internas a Orders, Delivery y Catalog para eliminar los datos personales de una cuenta.
 * Todas las operaciones de borrado son idempotentes en el servicio destino.
 */
public interface AccountErasurePort {

    record Blockers(int asClient, int asRestaurant, int asCourier) {
        public int total() {
            return asClient + asRestaurant + asCourier;
        }
    }

    /** Pedidos en curso de la persona. @throws com.flashdrop.auth.domain.exception.AccountErasureUnavailableException */
    Blockers checkBlockers(Long userId);

    /**
     * Orders anonimiza sus datos. @throws com.flashdrop.auth.domain.exception.AccountDeletionBlockedException
     * si aparecieron pedidos en curso (409) o AccountErasureUnavailableException si no responde.
     */
    void eraseOrders(Long userId);

    void eraseDelivery(Long userId);

    void eraseCatalog(Long userId);
}

package cl.flashdrop.orders.domain.exception;

/**
 * La persona todavia tiene pedidos en curso (como cliente, dueno de tienda o repartidor), asi que su
 * cuenta no se puede eliminar. Debe cancelarlos o cerrarlos por el flujo normal antes. HTTP 409.
 */
public class AccountDeletionBlockedException extends RuntimeException {

    public AccountDeletionBlockedException(String message) {
        super(message);
    }
}

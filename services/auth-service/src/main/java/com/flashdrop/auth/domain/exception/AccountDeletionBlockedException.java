package com.flashdrop.auth.domain.exception;

/**
 * La persona todavia tiene pedidos o rutas en curso: debe cancelarlos o cerrarlos por el flujo normal
 * antes de poder eliminar su cuenta (HTTP 409). Nada se cancela automaticamente.
 */
public class AccountDeletionBlockedException extends DomainException {
    public AccountDeletionBlockedException(String message) {
        super("ACCOUNT_HAS_ACTIVE_PROCESSES", message);
    }
}

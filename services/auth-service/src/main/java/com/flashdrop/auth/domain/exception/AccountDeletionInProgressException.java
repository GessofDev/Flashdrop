package com.flashdrop.auth.domain.exception;

/** La cuenta ya tiene una eliminacion en curso (HTTP 409). */
public class AccountDeletionInProgressException extends DomainException {
    public AccountDeletionInProgressException() {
        super("ACCOUNT_DELETION_IN_PROGRESS", "La eliminacion de la cuenta ya esta en curso");
    }
}

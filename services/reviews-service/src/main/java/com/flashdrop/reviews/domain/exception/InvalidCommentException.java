package com.flashdrop.reviews.domain.exception;

/**
 * El comentario supera los 500 caracteres (S5). Se traduce a HTTP 400.
 */
public class InvalidCommentException extends RuntimeException {

    public InvalidCommentException(int length) {
        super("Comentario demasiado largo: " + length + " caracteres. El maximo es 500.");
    }
}

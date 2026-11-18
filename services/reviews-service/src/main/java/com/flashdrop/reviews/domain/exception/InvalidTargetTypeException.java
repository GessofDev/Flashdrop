package com.flashdrop.reviews.domain.exception;

/**
 * El tipo de destino no es uno de los tres válidos (TIENDA | REPARTIDOR |
 * CLIENTE). Se traduce a HTTP 400.
 */
public class InvalidTargetTypeException extends RuntimeException {

    public InvalidTargetTypeException(String value) {
        super("Tipo de destino invalido: " + value
                + ". Valores permitidos: TIENDA, REPARTIDOR, CLIENTE.");
    }
}

package com.flashdrop.auth.domain.exception;

/**
 * Choque contra una restriccion unica al escribir. La capa de infraestructura
 * traduce el fallo de persistencia a esta excepcion de dominio, para que la
 * capa de aplicacion no dependa del framework.
 */
public class ResourceAlreadyExistsException extends DomainException {
    public ResourceAlreadyExistsException() {
        super("RESOURCE_ALREADY_EXISTS", "Ya existe un registro con esos datos");
    }
}

package com.flashdrop.auth.application.dto;

/**
 * Rol asignado a un usuario, expuesto únicamente entre microservicios.
 */
public record InternalRoleResponse(Long id, String name) {
}

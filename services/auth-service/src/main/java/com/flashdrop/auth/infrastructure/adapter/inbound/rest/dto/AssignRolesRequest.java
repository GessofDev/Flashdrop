package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

/**
 * Cuerpo de la asignacion de roles.
 *
 * <p>La lista define el conjunto final de roles del usuario: lo que no
 * aparece deja de estar asignado. No lleva anotaciones de validacion a
 * proposito, para que el caso de uso resuelva los errores con el mismo
 * formato que el resto de los endpoints internos.
 */
public record AssignRolesRequest(List<String> roles) {
}

package com.flashdrop.auth.application.dto;

import java.util.List;

/**
 * Resultado de asignar roles: el usuario y el conjunto de roles que quedo
 * persistido, con el nombre canonico de la tabla {@code roles}.
 */
public record AssignUserRolesResult(Long userId, List<InternalRoleResponse> roles) {
}

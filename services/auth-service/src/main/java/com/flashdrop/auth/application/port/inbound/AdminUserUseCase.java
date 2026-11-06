package com.flashdrop.auth.application.port.inbound;

import java.util.List;

import com.flashdrop.auth.application.dto.AdminUserResponse;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;
import com.flashdrop.auth.application.dto.TokenClaims;

/** Operaciones de administracion de usuarios (F6). Solo para administradores. */
public interface AdminUserUseCase {

    AdminUserResponse findByEmail(TokenClaims admin, String email);

    AssignUserRolesResult assignRoles(TokenClaims admin, Long targetUserId, List<String> roleNames, String clientIp);
}

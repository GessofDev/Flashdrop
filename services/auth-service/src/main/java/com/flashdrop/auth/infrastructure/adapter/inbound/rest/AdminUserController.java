package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.auth.application.dto.AdminUserResponse;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.AdminUserUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.AssignRolesRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Administracion de usuarios (F6): buscar por correo y asignar roles. El administrador es el usuario
 * con los roles Cliente, Restaurante y Repartidor a la vez; el token se valida aqui, como en /auth/profile.
 */
@RestController
@RequestMapping("/auth/admin")
public class AdminUserController {

    private final ValidateTokenUseCase validateToken;
    private final AdminUserUseCase adminUsers;

    public AdminUserController(ValidateTokenUseCase validateToken, AdminUserUseCase adminUsers) {
        this.validateToken = validateToken;
        this.adminUsers = adminUsers;
    }

    @GetMapping("/users")
    public AdminUserResponse findByEmail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String email) {
        return adminUsers.findByEmail(claims(authorization), email);
    }

    @PutMapping("/users/{userId}/roles")
    public AssignUserRolesResult assignRoles(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId,
            @RequestBody AssignRolesRequest request,
            HttpServletRequest http) {
        return adminUsers.assignRoles(claims(authorization), userId, request.roles(), clientIp(http));
    }

    private TokenClaims claims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }
        return validateToken.validate(authorization.substring(7));
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}

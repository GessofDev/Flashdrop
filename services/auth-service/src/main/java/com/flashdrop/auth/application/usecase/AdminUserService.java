package com.flashdrop.auth.application.usecase;

import java.util.List;
import java.util.Set;

import com.flashdrop.auth.application.dto.AdminUserResponse;
import com.flashdrop.auth.application.dto.AssignUserRolesCommand;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.AdminUserUseCase;
import com.flashdrop.auth.application.port.inbound.AssignUserRolesUseCase;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.AccessForbiddenException;
import com.flashdrop.auth.domain.exception.InvalidRoleException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.AdminAccess;
import com.flashdrop.auth.domain.valueobject.Email;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;

public class AdminUserService implements AdminUserUseCase {

    /** Roles que un administrador puede dar. Soporte sigue asignandose solo por el endpoint interno. */
    private static final Set<String> ASSIGNABLE = AdminAccess.ADMIN_ROLES;

    private final UserRepository users;
    private final AssignUserRolesUseCase assignUserRoles;

    public AdminUserService(UserRepository users, AssignUserRolesUseCase assignUserRoles) {
        this.users = users;
        this.assignUserRoles = assignUserRoles;
    }

    @Override
    public AdminUserResponse findByEmail(TokenClaims admin, String email) {
        requireAdmin(admin);
        Email normalized = new Email(email);
        User user = users.findByEmail(normalized)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + normalized.value()));
        return new AdminUserResponse(user.id(), user.name(), user.lastName(), user.email().value(),
                user.roles().stream().map(Role::name).toList());
    }

    @Override
    public AssignUserRolesResult assignRoles(TokenClaims admin, Long targetUserId, List<String> roleNames,
                                             String clientIp) {
        requireAdmin(admin);
        if (admin.userId().equals(targetUserId)) {
            throw new AccessForbiddenException("Un administrador no puede cambiar sus propios roles");
        }
        if (roleNames == null) {
            throw new InvalidRoleException("La lista de roles es obligatoria");
        }
        List<String> notAssignable = roleNames.stream()
                .filter(name -> name == null || !ASSIGNABLE.contains(name.trim()))
                .toList();
        if (!notAssignable.isEmpty()) {
            throw new InvalidRoleException("Rol no asignable por un administrador: " + notAssignable);
        }
        return assignUserRoles.assign(new AssignUserRolesCommand(targetUserId, roleNames, clientIp),
                "admin:" + admin.userId());
    }

    private static void requireAdmin(TokenClaims admin) {
        if (admin == null || !AdminAccess.isAdmin(admin.roles())) {
            throw new AccessForbiddenException("Se requiere un usuario administrador");
        }
    }
}

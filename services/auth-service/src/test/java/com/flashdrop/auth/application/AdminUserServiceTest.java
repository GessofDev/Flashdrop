package com.flashdrop.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.flashdrop.auth.application.dto.AdminUserResponse;
import com.flashdrop.auth.application.dto.AssignUserRolesCommand;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.inbound.AssignUserRolesUseCase;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.AdminUserService;
import com.flashdrop.auth.domain.exception.AccessForbiddenException;
import com.flashdrop.auth.domain.exception.InvalidRoleException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.AdminAccess;
import com.flashdrop.auth.domain.valueobject.Email;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;

class AdminUserServiceTest {

    private static final List<String> ALL_THREE = List.of("Cliente", "Restaurante", "Repartidor");
    private static final TokenClaims ADMIN = new TokenClaims(4L, "admin@demo.cl", ALL_THREE);

    private final UserRepository users = mock(UserRepository.class);
    private final AssignUserRolesUseCase assign = mock(AssignUserRolesUseCase.class);
    private final AdminUserService service = new AdminUserService(users, assign);

    @Test
    void adminAccess_requiresTheThreeRolesAtOnce() {
        assertEquals(true, AdminAccess.isAdmin(ALL_THREE));
        assertEquals(true, AdminAccess.isAdmin(List.of("Soporte", "Cliente", "Restaurante", "Repartidor")));
        assertEquals(false, AdminAccess.isAdmin(List.of("Cliente", "Restaurante")));
        assertEquals(false, AdminAccess.isAdmin(List.of("Soporte")));
        assertEquals(false, AdminAccess.isAdmin(List.of()));
        assertEquals(false, AdminAccess.isAdmin(null));
    }

    @Test
    void findByEmail_asAdmin_returnsIdentityAndRolesOnly() {
        User user = new User(9L, new Email("nuevo@correo.cl"), "11.111.111-1", "Ana", "Perez", "+56911111111",
                null, List.of(new Role(1L, "Cliente", "/c")), Instant.now());
        when(users.findByEmail(new Email("nuevo@correo.cl"))).thenReturn(Optional.of(user));

        AdminUserResponse result = service.findByEmail(ADMIN, "nuevo@correo.cl");

        assertEquals(new AdminUserResponse(9L, "Ana", "Perez", "nuevo@correo.cl", List.of("Cliente")), result);
    }

    @Test
    void findByEmail_unknownEmail_isNotFound() {
        when(users.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.findByEmail(ADMIN, "nadie@correo.cl"));
    }

    @Test
    void findByEmail_nonAdmin_isForbidden_andNothingIsRead() {
        TokenClaims cliente = new TokenClaims(1L, "cliente@demo.cl", List.of("Cliente"));

        assertThrows(AccessForbiddenException.class, () -> service.findByEmail(cliente, "x@y.cl"));
        verify(users, never()).findByEmail(any());
    }

    @Test
    void assignRoles_asAdmin_delegatesAuditingTheAdmin() {
        AssignUserRolesResult expected = new AssignUserRolesResult(9L, List.of());
        when(assign.assign(any(AssignUserRolesCommand.class), anyString())).thenReturn(expected);

        AssignUserRolesResult result = service.assignRoles(ADMIN, 9L, List.of("Cliente", "Restaurante"), "10.0.0.1");

        assertEquals(expected, result);
        ArgumentCaptor<AssignUserRolesCommand> command = ArgumentCaptor.forClass(AssignUserRolesCommand.class);
        verify(assign).assign(command.capture(), eq("admin:4"));
        assertEquals(9L, command.getValue().userId());
        assertEquals(List.of("Cliente", "Restaurante"), command.getValue().roleNames());
    }

    @Test
    void assignRoles_nonAdmin_isForbidden() {
        TokenClaims tienda = new TokenClaims(2L, "restaurante@demo.cl", List.of("Restaurante"));

        assertThrows(AccessForbiddenException.class,
                () -> service.assignRoles(tienda, 9L, List.of("Restaurante"), "ip"));
        verify(assign, never()).assign(any(), anyString());
    }

    @Test
    void assignRoles_onOneselfIsForbidden() {
        assertThrows(AccessForbiddenException.class,
                () -> service.assignRoles(ADMIN, 4L, List.of("Cliente"), "ip"));
        verify(assign, never()).assign(any(), anyString());
    }

    @Test
    void assignRoles_supportIsAssignableByAnAdmin_andDoesNotMakeAnAdmin() {
        AssignUserRolesResult expected = new AssignUserRolesResult(9L, List.of());
        when(assign.assign(any(AssignUserRolesCommand.class), anyString())).thenReturn(expected);

        assertEquals(expected, service.assignRoles(ADMIN, 9L, List.of("Cliente", "Soporte"), "ip"));

        verify(assign).assign(any(AssignUserRolesCommand.class), eq("admin:4"));
        assertEquals(false, AdminAccess.isAdmin(List.of("Cliente", "Soporte")));
    }

    @Test
    void assignRoles_supportCannotBeAssignedByANonAdmin() {
        TokenClaims soporte = new TokenClaims(8L, "soporte@demo.cl", List.of("Soporte"));

        assertThrows(AccessForbiddenException.class, () -> service.assignRoles(soporte, 9L, List.of("Soporte"), "ip"));
        verify(assign, never()).assign(any(), anyString());
    }

    @Test
    void assignRoles_unknownRolesAreNotAssignableByAnAdmin() {
        assertThrows(InvalidRoleException.class, () -> service.assignRoles(ADMIN, 9L, List.of("Superman"), "ip"));
        assertThrows(InvalidRoleException.class, () -> service.assignRoles(ADMIN, 9L, null, "ip"));
        verify(assign, never()).assign(any(), anyString());
    }
}

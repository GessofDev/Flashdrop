package com.flashdrop.auth.application;

import com.flashdrop.auth.application.dto.AssignUserRolesCommand;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.RoleRepository;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.AssignUserRolesService;
import com.flashdrop.auth.domain.exception.InvalidRoleException;
import com.flashdrop.auth.domain.exception.InvalidUserException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del caso de uso de asignacion de roles.
 *
 * <p>Puertos de salida mockeados: no toca base ni red.
 */
class AssignUserRolesServiceTest {

    private static final Role ROL_CLIENTE = new Role(1L, "Cliente", "/client/products/list");
    private static final Role ROL_RESTAURANTE = new Role(2L, "Restaurante", "/restaurant/orders/list");
    private static final Role ROL_REPARTIDOR = new Role(3L, "Repartidor", "/delivery/orders/list");
    private static final String IP = "10.0.0.7";

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private AssignUserRolesService service;

    @BeforeEach
    void setUp() {
        service = new AssignUserRolesService(users, roles, audit);
        when(roles.findAll()).thenReturn(List.of(ROL_CLIENTE, ROL_RESTAURANTE, ROL_REPARTIDOR));
    }

    private static AssignUserRolesCommand command(List<String> roles) {
        return new AssignUserRolesCommand(42L, roles, IP);
    }

    @Test
    @SuppressWarnings("unchecked")
    void asignaLosRolesPedidosYReemplazaElConjuntoAnterior() {
        var result = service.assign(command(List.of("Repartidor", "Cliente")));

        // El conjunto final se escribe completo: el dominio no conoce deltas.
        ArgumentCaptor<List<Role>> escritos = ArgumentCaptor.forClass(List.class);
        verify(users).assignRoles(eq(42L), escritos.capture());
        assertEquals(List.of(ROL_REPARTIDOR, ROL_CLIENTE), escritos.getValue());

        assertEquals(42L, result.userId());
        assertEquals(List.of("Repartidor", "Cliente"),
                result.roles().stream().map(r -> r.name()).toList());
    }

    @Test
    void rolDesconocidoLanzaInvalidRoleYNoEscribeNada() {
        assertThrows(InvalidRoleException.class, () -> service.assign(command(List.of("Chef"))));

        verify(users, never()).assignRoles(anyLong(), any());
    }

    /**
     * La existencia del usuario la verifica la escritura, dentro de su
     * transaccion: aca se propaga el error del puerto.
     */
    @Test
    void usuarioInexistenteLanzaUserNotFoundYQuedaAuditadoComoFallo() {
        doThrow(new UserNotFoundException("User not found with id: 42"))
                .when(users).assignRoles(eq(42L), any());

        assertThrows(UserNotFoundException.class, () -> service.assign(command(List.of("Cliente"))));

        verify(audit).record(argThat(e -> "ASSIGN_ROLES".equals(e.action())
                && "FAIL".equals(e.result())));
    }

    /** El nombre del rol no distingue mayusculas: "repartidor" es "Repartidor". */
    @Test
    void elNombreDelRolNoDistingueMayusculas() {
        var result = service.assign(command(List.of("repartidor")));

        assertEquals(List.of("Repartidor"),
                result.roles().stream().map(r -> r.name()).toList());
    }

    /** Un set vacio deja al usuario sin roles: es un PUT, no un PATCH. */
    @Test
    @SuppressWarnings("unchecked")
    void listaVaciaDejaAlUsuarioSinRoles() {
        var result = service.assign(command(List.of()));

        ArgumentCaptor<List<Role>> escritos = ArgumentCaptor.forClass(List.class);
        verify(users).assignRoles(eq(42L), escritos.capture());
        assertTrue(escritos.getValue().isEmpty());
        assertTrue(result.roles().isEmpty());
    }

    @Test
    void nombresRepetidosSeGuardanUnaVez() {
        var result = service.assign(command(List.of("Cliente", "cliente")));

        assertEquals(List.of("Cliente"),
                result.roles().stream().map(r -> r.name()).toList());
    }

    @Test
    void listaNulaLanzaInvalidRoleYNoEscribeNada() {
        assertThrows(InvalidRoleException.class, () -> service.assign(command(null)));

        verify(users, never()).assignRoles(anyLong(), any());
    }

    @Test
    void nombreEnBlancoLanzaInvalidRoleYNoEscribeNada() {
        assertThrows(InvalidRoleException.class, () -> service.assign(command(List.of("  "))));

        verify(users, never()).assignRoles(anyLong(), any());
    }

    /** Sin usuario destinatario el error es de usuario, no de rol. */
    @Test
    void usuarioNuloLanzaInvalidUserYNoEscribeNada() {
        assertThrows(InvalidUserException.class,
                () -> service.assign(new AssignUserRolesCommand(null, List.of("Cliente"), IP)));

        verify(users, never()).assignRoles(anyLong(), any());
    }

    /**
     * La auditoria de un cambio de permisos tiene que decir quien lo hizo y
     * sobre quien: el actor es el principal que ejecuto el cambio y el target
     * es el usuario afectado.
     */
    @Test
    void laAsignacionQuedaAuditadaConActorTargetYOrigen() {
        service.assign(command(List.of("Repartidor")));

        var evento = ArgumentCaptor.forClass(AuditLogger.AuditEvent.class);
        verify(audit).record(evento.capture());
        assertEquals("ASSIGN_ROLES", evento.getValue().action());
        assertEquals("internal-api", evento.getValue().actor());
        assertEquals("SUCCESS", evento.getValue().result());
        assertEquals(IP, evento.getValue().ip());
        assertEquals("42", evento.getValue().target());
    }

    @Test
    void unRolDesconocidoQuedaAuditadoComoFallo() {
        assertThrows(InvalidRoleException.class, () -> service.assign(command(List.of("Chef"))));

        verify(audit).record(argThat(e -> "ASSIGN_ROLES".equals(e.action())
                && "FAIL".equals(e.result())
                && "42".equals(e.target())));
    }

    /** El comando es inmutable: no puede quedar mutado por quien lo armo. */
    @Test
    void elComandoNoGuardaLaListaDelLlamador() {
        var roles = new ArrayList<>(List.of("Repartidor"));
        var cmd = new AssignUserRolesCommand(42L, roles, IP);

        roles.add("Cliente");

        assertEquals(List.of("Repartidor"), cmd.roleNames());
    }
}

package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.UserHasRoleEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRoleRepository;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataUserHasRoleRepository;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El adaptador JPA de la asignacion de roles.
 *
 * <p>Lo que importa aca es el orden de las operaciones: el borrado del
 * conjunto anterior tiene que estar flushed antes de insertar el nuevo, o un
 * rol que ya estaba asignado choca contra la restriccion unica
 * (id_user, id_rol) de la tabla puente.
 */
class JpaUserRepositoryAdapterTest {

    private static final Role ROL_REPARTIDOR = new Role(3L, "Repartidor", "/delivery/orders/list");
    private static final Role ROL_CLIENTE = new Role(1L, "Cliente", "/client/products/list");

    private final SpringDataUserRepository users = mock(SpringDataUserRepository.class);
    private final SpringDataUserHasRoleRepository userRoles = mock(SpringDataUserHasRoleRepository.class);
    private final SpringDataRoleRepository roles = mock(SpringDataRoleRepository.class);

    private JpaUserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaUserRepositoryAdapter(users, userRoles, roles);
        when(users.existsById(anyLong())).thenReturn(true);
    }

    @Test
    void borraElConjuntoAnteriorYDespuesInsertaElNuevo() {
        adapter.assignRoles(42L, List.of(ROL_REPARTIDOR, ROL_CLIENTE));

        // El orden importa: si los INSERT bajaran antes del flush, el borrado
        // todavia no habria llegado a la base y el rol repetido chocaría con
        // la restriccion unica. InOrder lo fija.
        InOrder orden = inOrder(userRoles);
        orden.verify(userRoles).deleteByUserId(42L);
        orden.verify(userRoles).flush();
        orden.verify(userRoles, times(2)).save(any(UserHasRoleEntity.class));

        ArgumentCaptor<UserHasRoleEntity> guardados = ArgumentCaptor.forClass(UserHasRoleEntity.class);
        verify(userRoles, times(2)).save(guardados.capture());

        List<UserHasRoleEntity> filas = guardados.getAllValues();
        assertEquals(42L, filas.get(0).getUserId());
        assertEquals(3L, filas.get(0).getRoleId());
        assertEquals(42L, filas.get(1).getUserId());
        assertEquals(1L, filas.get(1).getRoleId());
    }

    /** El conjunto vacio deja al usuario sin roles: borra y no inserta. */
    @Test
    void unaListaVaciaBorraYNoInsertaNada() {
        adapter.assignRoles(42L, List.of());

        InOrder orden = inOrder(userRoles);
        orden.verify(userRoles).deleteByUserId(42L);
        orden.verify(userRoles).flush();

        verify(userRoles, never()).save(any(UserHasRoleEntity.class));
    }

    /**
     * La existencia se verifica dentro de la misma transaccion que la
     * escritura: si el usuario no esta, no se toca la tabla puente.
     */
    @Test
    void unUsuarioInexistenteNoEscribeNada() {
        when(users.existsById(42L)).thenReturn(false);

        assertThrows(UserNotFoundException.class,
                () -> adapter.assignRoles(42L, List.of(ROL_REPARTIDOR)));

        verify(userRoles, never()).deleteByUserId(anyLong());
        verify(userRoles, never()).save(any(UserHasRoleEntity.class));
    }
}

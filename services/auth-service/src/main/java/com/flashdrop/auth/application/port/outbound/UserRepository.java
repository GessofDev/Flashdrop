package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);

    /** Resuelve varios usuarios en una sola consulta. Los ids inexistentes
     *  simplemente no aparecen en el resultado. */
    List<User> findAllByIds(List<Long> ids);

    Optional<User> findByEmail(Email email);
    boolean existsByEmail(Email email);

    /**
     * Reemplaza el conjunto de roles del usuario por el dado.
     *
     * <p>Es una operacion de reemplazo, no de delta: lo que no viene en la
     * lista deja de estar asignado. Una lista vacia deja al usuario sin
     * roles.
     *
     * <p>La escritura es atomica con la verificacion de existencia: si el
     * usuario no existe, no se escribe nada.
     */
    void assignRoles(Long userId, List<Role> roles);
}

package com.flashdrop.auth.application.port.outbound;

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
}

package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByEmail(Email email);
    boolean existsByEmail(Email email);
}

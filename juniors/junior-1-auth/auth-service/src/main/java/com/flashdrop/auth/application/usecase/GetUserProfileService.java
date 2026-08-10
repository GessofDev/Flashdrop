package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.UserProfile;
import com.flashdrop.auth.application.port.inbound.GetUserProfileUseCase;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.User;

public class GetUserProfileService implements GetUserProfileUseCase {

    private final UserRepository users;

    public GetUserProfileService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserProfile getProfile(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        return new UserProfile(user.id(), user.name(), user.lastName(), user.email().value(),
                user.phone(), user.rut(), user.photo(), user.roleNames());
    }
}

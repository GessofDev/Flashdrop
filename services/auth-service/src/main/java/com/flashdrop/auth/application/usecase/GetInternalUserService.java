package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.InternalRoleResponse;
import com.flashdrop.auth.application.dto.InternalUserResponse;
import com.flashdrop.auth.application.port.inbound.GetInternalUserUseCase;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.User;

import java.util.List;
import java.util.Objects;

public class GetInternalUserService implements GetInternalUserUseCase {

    private final UserRepository users;

    public GetInternalUserService(UserRepository users) {
        this.users = users;
    }

    @Override
    public InternalUserResponse getUser(Long userId) {
        User user = requireUser(userId);
        return new InternalUserResponse(
                user.id(),
                user.name(),
                user.lastName(),
                user.email().value(),
                user.phone());
    }

    @Override
    public List<InternalUserResponse> getUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> distintos = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distintos.isEmpty()) {
            return List.of();
        }
        return users.findAllByIds(distintos).stream()
                .map(user -> new InternalUserResponse(
                        user.id(),
                        user.name(),
                        user.lastName(),
                        user.email().value(),
                        user.phone()))
                .toList();
    }

    @Override
    public List<InternalRoleResponse> getRoles(Long userId) {
        return requireUser(userId).roles().stream()
                .map(role -> new InternalRoleResponse(role.id(), role.name()))
                .toList();
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }
}

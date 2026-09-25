package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.UpdateUserProfileCommand;
import com.flashdrop.auth.application.dto.UserProfile;
import com.flashdrop.auth.application.port.inbound.UpdateUserProfileUseCase;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Phone;

/**
 * Edición del perfil propio ({@code PUT /auth/profile}).
 *
 * <p>Reemplaza los cuatro campos editables completos: el teléfono o la foto
 * que lleguen en null quedan en null. Así se quitan, sin un endpoint aparte.
 *
 * <p>El teléfono pasa por {@link Phone}, igual que en el alta. Sin eso el mismo
 * número podría quedar guardado con y sin espacios, y la restricción única de
 * {@code users.phone} no lo detectaría. Si choca con el de otro usuario, la
 * base lo rechaza y {@code GlobalExceptionHandler} lo devuelve como 409.
 */
public class UpdateUserProfileService implements UpdateUserProfileUseCase {

    private final UserRepository users;

    public UpdateUserProfileService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserProfile updateProfile(Long userId, UpdateUserProfileCommand command) {
        User actual = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        String phone = command.phone() == null ? null : new Phone(command.phone()).value();

        User guardado = users.save(actual.conPerfil(
                command.name(), command.lastName(), phone, command.photo()));

        return new UserProfile(guardado.id(), guardado.name(), guardado.lastName(),
                guardado.email().value(), guardado.phone(), guardado.rut(), guardado.photo(),
                guardado.roleNames());
    }
}

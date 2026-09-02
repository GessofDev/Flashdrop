package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.RegisterUserCommand;
import com.flashdrop.auth.application.dto.RegisterUserResult;
import com.flashdrop.auth.application.port.inbound.RegisterUserUseCase;
import com.flashdrop.auth.application.port.outbound.*;
import com.flashdrop.auth.domain.PasswordPolicy;
import com.flashdrop.auth.domain.exception.EmailAlreadyRegisteredException;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import com.flashdrop.auth.domain.valueobject.Phone;

import java.time.Instant;
import java.util.List;

public class RegisterUserService implements RegisterUserUseCase {

    private static final String DEFAULT_ROLE = "Cliente";

    private final UserRepository users;
    private final CredentialStore credentials;
    private final RoleRepository roles;
    private final PasswordHasher hasher;
    private final AuditLogger audit;

    public RegisterUserService(UserRepository users, CredentialStore credentials, RoleRepository roles,
                               PasswordHasher hasher, AuditLogger audit) {
        this.users = users;
        this.credentials = credentials;
        this.roles = roles;
        this.hasher = hasher;
        this.audit = audit;
    }

    /**
     * N-1: el alta se audita en los dos desenlaces.
     *
     * <p>Antes solo se registraba el exito, asi que un intento de registro
     * rechazado —correo ya usado, contrasena debil, formato invalido, telefono
     * repetido— no dejaba rastro. Eso deja ciego el registro de auditoria
     * justamente ante el patron que interesa detectar: alguien probando altas
     * en serie para averiguar que correos ya existen. El login ya lo hacia.
     *
     * <p>Se anota el correo tal como llego, no el normalizado, porque cuando el
     * fallo es de formato no existe una version validada.
     */
    @Override
    public RegisterUserResult register(RegisterUserCommand command) {
        String intento = command.email() == null ? "(sin email)" : command.email().trim();
        try {
            return registrar(command);
        } catch (RuntimeException e) {
            audit.record(new AuditLogger.AuditEvent("REGISTER", intento, "FAIL", null));
            throw e;
        }
    }

    private RegisterUserResult registrar(RegisterUserCommand command) {
        var email = new Email(command.email());              // valida formato (dominio)
        PasswordPolicy.validate(command.rawPassword());       // S-13
        String phone = command.phone() == null ? null : new Phone(command.phone()).value();

        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();       // 409, sin decir cuál campo
        }

        Role defaultRole = roles.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Rol base no configurado: " + DEFAULT_ROLE));

        User createdUser = users.save(new User(null, email, command.rut(), command.name(), command.lastName(),
                phone, null, List.of(defaultRole), Instant.now()));

        credentials.save(new Credentials(null, createdUser.id(), email.value(),
                hasher.hash(command.rawPassword()), "ACTIVE"));

        audit.record(new AuditLogger.AuditEvent("REGISTER", email.value(), "SUCCESS", null));
        return new RegisterUserResult(createdUser.id());
    }
}

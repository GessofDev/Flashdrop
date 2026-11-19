package com.flashdrop.auth.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flashdrop.auth.application.dto.UserProfile;
import com.flashdrop.auth.application.port.inbound.ProfilePhotoUseCase;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.ProfilePhotoProcessor;
import com.flashdrop.auth.application.port.outbound.ProfilePhotoStorage;
import com.flashdrop.auth.application.port.outbound.RateLimiter;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.DomainException;
import com.flashdrop.auth.domain.exception.ProfilePhotoNotFoundException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.User;

/**
 * Foto del perfil propio (F10). El usuario sale siempre del token: no hay
 * forma de tocar la foto de otro.
 *
 * <p>Orden al reemplazar: se guarda el archivo nuevo, luego la clave en
 * {@code users.photo} y al final se borra el archivo anterior. Si falla el
 * almacenamiento, el usuario conserva su foto anterior; si falla la base, se
 * borra el archivo recien subido para no dejar huerfanos. Un archivo anterior
 * que no se pudo borrar solo se registra en el log: el usuario ya tiene su foto nueva.
 */
public class ProfilePhotoService implements ProfilePhotoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProfilePhotoService.class);
    private static final String UPLOAD = "PROFILE_PHOTO_UPLOAD";
    private static final String DELETE = "PROFILE_PHOTO_DELETE";

    private final UserRepository users;
    private final ProfilePhotoStorage storage;
    private final ProfilePhotoProcessor processor;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;

    public ProfilePhotoService(UserRepository users, ProfilePhotoStorage storage, ProfilePhotoProcessor processor,
                               RateLimiter rateLimiter, AuditLogger audit) {
        this.users = users;
        this.storage = storage;
        this.processor = processor;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Override
    public UserProfile upload(Long userId, byte[] content, String declaredContentType) {
        User user = findUser(userId);
        String actor = String.valueOf(userId);

        // Cuenta cada intento, valido o no: evita que alguien use el endpoint para
        // procesar imagenes sin limite.
        String rateKey = "photo:" + userId;
        rateLimiter.checkAllowed(rateKey);
        rateLimiter.recordFailure(rateKey);

        byte[] jpeg;
        try {
            jpeg = processor.normalize(content, declaredContentType);
        } catch (DomainException invalid) {
            audit.record(new AuditLogger.AuditEvent(UPLOAD, actor, "FAIL", null));
            throw invalid;
        }

        String newKey = ProfilePhotos.newKey(userId);
        storage.put(newKey, jpeg, ProfilePhotoProcessor.OUTPUT_CONTENT_TYPE);

        User saved;
        try {
            saved = users.save(user.conFoto(newKey));
        } catch (RuntimeException e) {
            deleteQuietly(newKey);
            throw e;
        }

        String previous = user.photo();
        if (ProfilePhotos.isStoredKey(previous, userId) && !previous.equals(newKey)) {
            deleteQuietly(previous);
        }
        audit.record(new AuditLogger.AuditEvent(UPLOAD, actor, "SUCCESS", null));
        return toProfile(saved);
    }

    @Override
    public byte[] get(Long userId) {
        User user = findUser(userId);
        if (!ProfilePhotos.isStoredKey(user.photo(), userId)) {
            throw new ProfilePhotoNotFoundException();
        }
        return storage.get(user.photo()).orElseThrow(ProfilePhotoNotFoundException::new);
    }

    /** Idempotente: quitar una foto que no existe responde igual. */
    @Override
    public void delete(Long userId) {
        User user = findUser(userId);
        String previous = user.photo();
        if (previous == null) {
            return;
        }
        users.save(user.conFoto(null));
        if (ProfilePhotos.isStoredKey(previous, userId)) {
            deleteQuietly(previous);
        }
        audit.record(new AuditLogger.AuditEvent(DELETE, String.valueOf(userId), "SUCCESS", null));
    }

    private User findUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar la foto de perfil {}: {}", key, e.getMessage());
        }
    }

    private static UserProfile toProfile(User user) {
        return new UserProfile(user.id(), user.name(), user.lastName(), user.email().value(),
                user.phone(), user.rut(), ProfilePhotos.urlFor(user.photo(), user.id()), user.roleNames());
    }
}

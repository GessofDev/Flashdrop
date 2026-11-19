package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.dto.UserProfile;
import com.flashdrop.auth.application.port.inbound.ProfilePhotoUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.InvalidProfilePhotoException;
import com.flashdrop.auth.domain.exception.InvalidTokenException;

/**
 * Foto del perfil propio (F10). Igual que {@code /auth/profile}, el token se
 * valida aqui y el usuario sale de el, nunca de la peticion.
 */
@RestController
@RequestMapping("/auth/profile/photo")
public class ProfilePhotoController {

    private final ValidateTokenUseCase validateToken;
    private final ProfilePhotoUseCase photos;

    public ProfilePhotoController(ValidateTokenUseCase validateToken, ProfilePhotoUseCase photos) {
        this.validateToken = validateToken;
        this.photos = photos;
    }

    /** Sube o reemplaza la foto. Multipart con el campo {@code file}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfile upload(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @RequestPart("file") MultipartFile file) {
        TokenClaims claims = validateToken.validate(bearer(authorization));
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidProfilePhotoException("No se pudo leer la foto enviada");
        }
        return photos.upload(claims.userId(), content, file.getContentType());
    }

    /**
     * La URL que entrega el perfil lleva {@code ?v=}, que cambia con cada foto: por eso se
     * puede guardar un dia en la cache del dispositivo, pero nunca en una cache compartida.
     */
    @GetMapping
    public ResponseEntity<byte[]> get(@RequestHeader(value = "Authorization", required = false) String authorization) {
        TokenClaims claims = validateToken.validate(bearer(authorization));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(photos.get(claims.userId()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization) {
        TokenClaims claims = validateToken.validate(bearer(authorization));
        photos.delete(claims.userId());
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }
        return authorization.substring(7);
    }
}

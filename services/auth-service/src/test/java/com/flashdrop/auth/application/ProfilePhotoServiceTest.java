package com.flashdrop.auth.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.flashdrop.auth.application.dto.UserProfile;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.ProfilePhotoProcessor;
import com.flashdrop.auth.application.port.outbound.ProfilePhotoStorage;
import com.flashdrop.auth.application.port.outbound.RateLimiter;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.ProfilePhotoService;
import com.flashdrop.auth.domain.exception.InvalidProfilePhotoException;
import com.flashdrop.auth.domain.exception.PhotoStorageUnavailableException;
import com.flashdrop.auth.domain.exception.ProfilePhotoNotFoundException;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.domain.exception.UserNotFoundException;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;

/** F10: subir, leer y quitar la foto del perfil propio. */
class ProfilePhotoServiceTest {

    private static final Long USER = 7L;
    private static final String OLD_KEY = "avatars/7/11111111-1111-4111-8111-111111111111.jpg";
    private static final byte[] RAW = {1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9};

    private final UserRepository users = mock(UserRepository.class);
    private final ProfilePhotoStorage storage = mock(ProfilePhotoStorage.class);
    private final ProfilePhotoProcessor processor = mock(ProfilePhotoProcessor.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final ProfilePhotoService service =
            new ProfilePhotoService(users, storage, processor, rateLimiter, audit);

    @BeforeEach
    void setUp() {
        when(processor.normalize(RAW, "image/jpeg")).thenReturn(JPEG);
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void userWithPhoto(String photo) {
        when(users.findById(USER)).thenReturn(Optional.of(new User(USER, new Email("cliente@demo.cl"),
                "11.111.111-1", "Cliente", "Demo", "+56911111111", photo,
                List.of(new Role(1L, "Cliente", "/client")), Instant.parse("2026-01-01T00:00:00Z"))));
    }

    private User saved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ subir

    @Test
    void subirGuardaElJpegNormalizadoYDevuelveLaUrlDeAuth() {
        userWithPhoto(null);

        UserProfile profile = service.upload(USER, RAW, "image/jpeg");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq(JPEG), eq("image/jpeg"));
        assertTrue(key.getValue().matches("avatars/7/[0-9a-f-]{36}\\.jpg"), key.getValue());
        assertEquals(key.getValue(), saved().photo());
        String id = key.getValue().substring("avatars/7/".length(), key.getValue().length() - 4);
        assertEquals("/auth/profile/photo?v=" + id, profile.photo());
        verify(audit).record(argThat(e -> e.action().equals("PROFILE_PHOTO_UPLOAD") && e.result().equals("SUCCESS")));
    }

    /** Orden: archivo nuevo, base y recien ahi se borra el anterior. */
    @Test
    void reemplazarBorraLaFotoAnteriorDespuesDeGuardarLaNueva() {
        userWithPhoto(OLD_KEY);

        service.upload(USER, RAW, "image/jpeg");

        InOrder order = inOrder(storage, users);
        order.verify(storage).put(anyString(), eq(JPEG), eq("image/jpeg"));
        order.verify(users).save(any());
        order.verify(storage).delete(OLD_KEY);
    }

    /** Un valor de antes de F10 (una URL suelta) no es un archivo nuestro: no se intenta borrar. */
    @Test
    void unValorViejoQueNoEsClaveNoSeIntentaBorrar() {
        userWithPhoto("https://img.flashdrop.cl/perfil.png");

        service.upload(USER, RAW, "image/jpeg");

        verify(storage, never()).delete(anyString());
    }

    @Test
    void siFallaElAlmacenamientoElUsuarioConservaSuFoto() {
        userWithPhoto(OLD_KEY);
        doThrow(new PhotoStorageUnavailableException("Floci no responde"))
                .when(storage).put(anyString(), any(), anyString());

        assertThrows(PhotoStorageUnavailableException.class, () -> service.upload(USER, RAW, "image/jpeg"));

        verify(users, never()).save(any());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void siFallaLaBaseSeBorraElArchivoRecienSubido() {
        userWithPhoto(OLD_KEY);
        doThrow(new IllegalStateException("base caida")).when(users).save(any());

        assertThrows(IllegalStateException.class, () -> service.upload(USER, RAW, "image/jpeg"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyString());
        verify(storage).delete(key.getValue());
        verify(storage, never()).delete(OLD_KEY);
    }

    @Test
    void siNoSePuedeBorrarLaAnteriorLaSubidaIgualResulta() {
        userWithPhoto(OLD_KEY);
        doThrow(new PhotoStorageUnavailableException("Floci no responde")).when(storage).delete(OLD_KEY);

        UserProfile profile = service.upload(USER, RAW, "image/jpeg");

        assertTrue(profile.photo().startsWith("/auth/profile/photo?v="));
    }

    @Test
    void unaFotoInvalidaNoSeGuardaYQuedaAuditada() {
        userWithPhoto(null);
        when(processor.normalize(RAW, "image/gif")).thenThrow(new InvalidProfilePhotoException("La foto debe ser JPEG o PNG"));

        assertThrows(InvalidProfilePhotoException.class, () -> service.upload(USER, RAW, "image/gif"));

        verify(storage, never()).put(anyString(), any(), anyString());
        verify(users, never()).save(any());
        verify(audit).record(argThat(e -> e.action().equals("PROFILE_PHOTO_UPLOAD") && e.result().equals("FAIL")));
    }

    @Test
    void conDemasiadasSubidasSeCortaAntesDeProcesar() {
        userWithPhoto(null);
        doThrow(new RateLimitExceededException()).when(rateLimiter).checkAllowed("photo:7");

        assertThrows(RateLimitExceededException.class, () -> service.upload(USER, RAW, "image/jpeg"));

        verify(processor, never()).normalize(any(), any());
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void cadaSubidaCuentaParaElLimite() {
        userWithPhoto(null);

        service.upload(USER, RAW, "image/jpeg");

        verify(rateLimiter).checkAllowed("photo:7");
        verify(rateLimiter).recordFailure("photo:7");
    }

    @Test
    void usuarioInexistente() {
        when(users.findById(USER)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.upload(USER, RAW, "image/jpeg"));
    }

    // ------------------------------------------------------------------ leer

    @Test
    void leerDevuelveElArchivoGuardado() {
        userWithPhoto(OLD_KEY);
        when(storage.get(OLD_KEY)).thenReturn(Optional.of(JPEG));

        assertArrayEquals(JPEG, service.get(USER));
    }

    @Test
    void leerSinFotoDa404() {
        userWithPhoto(null);

        assertThrows(ProfilePhotoNotFoundException.class, () -> service.get(USER));
    }

    /** Una clave de otro usuario en la fila (dato corrupto) no se sirve. */
    @Test
    void leerNoSirveUnaClaveDeOtroUsuario() {
        userWithPhoto("avatars/8/11111111-1111-4111-8111-111111111111.jpg");

        assertThrows(ProfilePhotoNotFoundException.class, () -> service.get(USER));
        verify(storage, never()).get(anyString());
    }

    @Test
    void leerCuandoElArchivoYaNoExisteDa404() {
        userWithPhoto(OLD_KEY);
        when(storage.get(OLD_KEY)).thenReturn(Optional.empty());

        assertThrows(ProfilePhotoNotFoundException.class, () -> service.get(USER));
    }

    // ------------------------------------------------------------------ quitar

    @Test
    void quitarLimpiaLaFilaYBorraElArchivo() {
        userWithPhoto(OLD_KEY);

        service.delete(USER);

        assertNull(saved().photo());
        verify(storage).delete(OLD_KEY);
        verify(audit).record(argThat(e -> e.action().equals("PROFILE_PHOTO_DELETE")));
    }

    @Test
    void quitarSinFotoNoHaceNada() {
        userWithPhoto(null);

        service.delete(USER);

        verify(users, never()).save(any());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void quitarUnValorViejoLimpiaLaFilaSinTocarElAlmacenamiento() {
        userWithPhoto("https://img.flashdrop.cl/perfil.png");

        service.delete(USER);

        assertNull(saved().photo());
        verify(storage, never()).delete(anyString());
    }
}

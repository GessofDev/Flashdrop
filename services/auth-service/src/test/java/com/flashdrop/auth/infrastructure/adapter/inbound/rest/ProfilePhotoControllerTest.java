package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.dto.UserProfile;
import com.flashdrop.auth.application.port.inbound.ProfilePhotoUseCase;
import com.flashdrop.auth.application.port.inbound.ValidateTokenUseCase;
import com.flashdrop.auth.domain.exception.InvalidProfilePhotoException;
import com.flashdrop.auth.domain.exception.PhotoStorageUnavailableException;
import com.flashdrop.auth.domain.exception.ProfilePhotoNotFoundException;
import com.flashdrop.auth.domain.exception.ProfilePhotoTooLargeException;
import com.flashdrop.auth.domain.exception.RateLimitExceededException;
import com.flashdrop.auth.infrastructure.exception.GlobalExceptionHandler;

/**
 * Contrato HTTP de /auth/profile/photo. Como en {@code AuthControllerTest}, se monta
 * con {@code standaloneSetup}: la cadena de seguridad la cubre {@code ApplicationStartupTest}.
 */
class ProfilePhotoControllerTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};

    private final ValidateTokenUseCase validateToken = mock(ValidateTokenUseCase.class);
    private final ProfilePhotoUseCase photos = mock(ProfilePhotoUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new ProfilePhotoController(validateToken, photos))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(validateToken.validate("un-token"))
                .thenReturn(new TokenClaims(7L, "cliente@demo.cl", List.of("Cliente")));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg", JPEG);
    }

    @Test
    void subirDevuelve200ConElPerfilYLaUrlDeLaFoto() throws Exception {
        when(photos.upload(eq(7L), any(), eq("image/jpeg"))).thenReturn(new UserProfile(7L, "Cliente", "Demo",
                "cliente@demo.cl", null, null, "/auth/profile/photo?v=abc", List.of("Cliente")));

        mvc.perform(multipart("/auth/profile/photo").file(file()).header("Authorization", "Bearer un-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.photo").value("/auth/profile/photo?v=abc"));
    }

    @Test
    void subirSinTokenDa401YNoProcesaNada() throws Exception {
        mvc.perform(multipart("/auth/profile/photo").file(file()))
                .andExpect(status().isUnauthorized());

        verify(photos, never()).upload(any(), any(), any());
    }

    @Test
    void subirSinElCampoFileDa400() throws Exception {
        mvc.perform(multipart("/auth/profile/photo")
                        .file(new MockMultipartFile("otro", "x.jpg", "image/jpeg", JPEG))
                        .header("Authorization", "Bearer un-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void fotoInvalidaDa400() throws Exception {
        when(photos.upload(eq(7L), any(), any())).thenThrow(new InvalidProfilePhotoException("La foto debe ser JPEG o PNG"));

        mvc.perform(multipart("/auth/profile/photo").file(file()).header("Authorization", "Bearer un-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_PHOTO_INVALID"))
                .andExpect(jsonPath("$.message").value("La foto debe ser JPEG o PNG"));
    }

    @Test
    void fotoDemasiadoGrandeDa413() throws Exception {
        when(photos.upload(eq(7L), any(), any())).thenThrow(new ProfilePhotoTooLargeException("La foto supera el límite de 2 MB"));

        mvc.perform(multipart("/auth/profile/photo").file(file()).header("Authorization", "Bearer un-token"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AUTH_PHOTO_TOO_LARGE"));
    }

    @Test
    void demasiadasSubidasDa429() throws Exception {
        when(photos.upload(eq(7L), any(), any())).thenThrow(new RateLimitExceededException());

        mvc.perform(multipart("/auth/profile/photo").file(file()).header("Authorization", "Bearer un-token"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void almacenamientoCaidoDa503SinDetallesInternos() throws Exception {
        when(photos.upload(eq(7L), any(), any())).thenThrow(
                new PhotoStorageUnavailableException("x", new RuntimeException("http://floci:4566 refused")));

        mvc.perform(multipart("/auth/profile/photo").file(file()).header("Authorization", "Bearer un-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("El almacenamiento de fotos no está disponible"));
    }

    @Test
    void leerDevuelveElJpegConCachePrivada() throws Exception {
        when(photos.get(7L)).thenReturn(JPEG);

        mvc.perform(get("/auth/profile/photo").header("Authorization", "Bearer un-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", "max-age=86400, private"));
    }

    @Test
    void leerSinFotoDa404() throws Exception {
        when(photos.get(7L)).thenThrow(new ProfilePhotoNotFoundException());

        mvc.perform(get("/auth/profile/photo").header("Authorization", "Bearer un-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_PHOTO_NOT_FOUND"));
    }

    @Test
    void leerSinTokenDa401() throws Exception {
        mvc.perform(get("/auth/profile/photo")).andExpect(status().isUnauthorized());
    }

    @Test
    void quitarDevuelve204() throws Exception {
        mvc.perform(delete("/auth/profile/photo").header("Authorization", "Bearer un-token"))
                .andExpect(status().isNoContent());

        verify(photos).delete(7L);
    }
}

package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.flashdrop.catalog.application.storage.StoredProductImage;
import com.flashdrop.catalog.application.usecase.GetProductImageUseCase;
import com.flashdrop.catalog.application.usecase.UploadProductImageUseCase;
import com.flashdrop.catalog.domain.exception.ImageStorageException;
import com.flashdrop.catalog.domain.exception.PayloadTooLargeException;

/**
 * Plan de pruebas §3.3: {@code POST /api/catalog/my/products/image} — recepción del
 * {@code MultipartFile}, respuesta 201 con {@code {objectKey, url}}, 400 por tipo no
 * soportado, 413 si excede el tamaño y 502 ante falla del almacenamiento.
 *
 * <p>Slice web con {@code MockMvc} standalone y el caso de uso <b>simulado</b>
 * ({@link UploadProductImageUseCase}, Mockito): no levanta Spring Boot ni base de datos.
 * Los errores 400/413/502 los decide el caso de uso (probado en
 * {@code UploadProductImageUseCaseTest}); acá se comprueba que el controller los traduzca
 * al código HTTP y al cuerpo de error correctos mediante {@link RestExceptionHandler}.
 * La seguridad (401/403) y el filtro de tamaño del servidor se prueban en
 * {@code ProductImageControllerIT}.</p>
 */
class ProductImageControllerTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private UploadProductImageUseCase uploadProductImageUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        uploadProductImageUseCase = mock(UploadProductImageUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductImageController(uploadProductImageUseCase, mock(GetProductImageUseCase.class)))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    private MockMultipartFile file(String contentType) {
        return new MockMultipartFile("file", "producto.img", contentType, JPEG);
    }

    @Test
    void upload_passesTheReceivedFileToTheUseCase_andReturnsObjectKeyAndUrl() throws Exception {
        StoredProductImage stored = new StoredProductImage(
                "products/2026/10/550e8400-e29b-41d4-a716-446655440000.jpg",
                "/catalog/images/products/2026/10/550e8400-e29b-41d4-a716-446655440000.jpg");
        when(uploadProductImageUseCase.execute(any(byte[].class), eq("image/jpeg"))).thenReturn(stored);

        mockMvc.perform(multipart("/api/catalog/my/products/image").file(file("image/jpeg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectKey").value(stored.objectKey()))
                .andExpect(jsonPath("$.url").value(stored.url()));

        ArgumentCaptor<byte[]> received = ArgumentCaptor.forClass(byte[].class);
        verify(uploadProductImageUseCase).execute(received.capture(), eq("image/jpeg"));
        assertArrayEquals(JPEG, received.getValue());
    }

    @Test
    void upload_whenTheUseCaseRejectsTheMimeType_returnsBadRequest() throws Exception {
        when(uploadProductImageUseCase.execute(any(byte[].class), eq("text/plain")))
                .thenThrow(new IllegalArgumentException("La imagen debe ser JPEG, PNG o WebP"));

        mockMvc.perform(multipart("/api/catalog/my/products/image").file(file("text/plain")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("La imagen debe ser JPEG, PNG o WebP"));
    }

    @Test
    void upload_whenTheImageExceedsTheLimit_returnsPayloadTooLarge() throws Exception {
        when(uploadProductImageUseCase.execute(any(byte[].class), eq("image/jpeg")))
                .thenThrow(new PayloadTooLargeException("La imagen supera el limite de 5 MB"));

        mockMvc.perform(multipart("/api/catalog/my/products/image").file(file("image/jpeg")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("La imagen supera el limite de 5 MB"));
    }

    @Test
    void upload_whenTheStorageFails_returnsBadGateway() throws Exception {
        when(uploadProductImageUseCase.execute(any(byte[].class), eq("image/jpeg")))
                .thenThrow(new ImageStorageException("S3 no disponible"));

        mockMvc.perform(multipart("/api/catalog/my/products/image").file(file("image/jpeg")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("IMAGE_STORAGE_ERROR"))
                .andExpect(jsonPath("$.message").value("S3 no disponible"));
    }
}

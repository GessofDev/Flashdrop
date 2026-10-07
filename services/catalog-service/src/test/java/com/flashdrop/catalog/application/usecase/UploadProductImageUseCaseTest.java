package com.flashdrop.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.flashdrop.catalog.application.port.outbound.ProductImageStorage;
import com.flashdrop.catalog.application.storage.StoredProductImage;
import com.flashdrop.catalog.domain.exception.PayloadTooLargeException;

class UploadProductImageUseCaseTest {

    @Mock
    private ProductImageStorage productImageStorage;

    private UploadProductImageUseCase useCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new UploadProductImageUseCase(productImageStorage);
    }

    @Test
    void execute_validJpeg_storesDetectedImageType() {
        byte[] image = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        StoredProductImage stored = new StoredProductImage("products/2026/09/test.jpg", "https://example/test.jpg");
        when(productImageStorage.store(any(byte[].class), eq("image/jpeg"), eq("jpg")))
                .thenReturn(stored);

        StoredProductImage result = useCase.execute(image, "image/jpeg");

        assertEquals(stored, result);
        verify(productImageStorage).store(image, "image/jpeg", "jpg");
    }

    /** Plan de pruebas §2.3: formato permitido PNG. */
    @Test
    void execute_validPng_storesDetectedImageType() {
        byte[] image = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        StoredProductImage stored = new StoredProductImage("products/2026/10/test.png", "https://example/test.png");
        when(productImageStorage.store(any(byte[].class), eq("image/png"), eq("png")))
                .thenReturn(stored);

        StoredProductImage result = useCase.execute(image, "image/png");

        assertEquals(stored, result);
        verify(productImageStorage).store(image, "image/png", "png");
    }

    /** Plan de pruebas §2.3: formato permitido WebP (cabecera "RIFF", 4 bytes de tamaño, "WEBP"). */
    @Test
    void execute_validWebp_storesDetectedImageType() {
        byte[] image = {
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P'
        };
        StoredProductImage stored = new StoredProductImage("products/2026/10/test.webp", "https://example/test.webp");
        when(productImageStorage.store(any(byte[].class), eq("image/webp"), eq("webp")))
                .thenReturn(stored);

        StoredProductImage result = useCase.execute(image, "image/webp");

        assertEquals(stored, result);
        verify(productImageStorage).store(image, "image/webp", "webp");
    }

    /** Plan de pruebas §2.3: tamaño máximo "<= 5MB" — el límite exacto se acepta. */
    @Test
    void execute_fileOfExactlyFiveMegabytes_isAccepted() {
        byte[] image = new byte[UploadProductImageUseCase.MAX_IMAGE_BYTES];
        image[0] = (byte) 0xFF;
        image[1] = (byte) 0xD8;
        image[2] = (byte) 0xFF;
        StoredProductImage stored = new StoredProductImage("products/2026/10/test.jpg", "https://example/test.jpg");
        when(productImageStorage.store(any(byte[].class), eq("image/jpeg"), eq("jpg")))
                .thenReturn(stored);

        StoredProductImage result = useCase.execute(image, "image/jpeg");

        assertEquals(stored, result);
        verify(productImageStorage).store(image, "image/jpeg", "jpg");
    }

    @Test
    void execute_contentTypeDoesNotMatch_rejectsUpload() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(png, "image/jpeg")
        );

        assertEquals("El Content-Type no coincide con el contenido de la imagen", exception.getMessage());
    }

    @Test
    void execute_fileLargerThanFiveMegabytes_rejectsUpload() {
        byte[] oversized = new byte[UploadProductImageUseCase.MAX_IMAGE_BYTES + 1];

        PayloadTooLargeException exception = assertThrows(
                PayloadTooLargeException.class,
                () -> useCase.execute(oversized, "image/jpeg")
        );

        assertEquals("La imagen supera el limite de 5 MB", exception.getMessage());
    }
}

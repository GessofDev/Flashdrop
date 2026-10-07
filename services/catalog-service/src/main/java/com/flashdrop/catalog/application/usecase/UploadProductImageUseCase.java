package com.flashdrop.catalog.application.usecase;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductImageStorage;
import com.flashdrop.catalog.application.storage.StoredProductImage;
import com.flashdrop.catalog.domain.exception.PayloadTooLargeException;

@Service
public class UploadProductImageUseCase {

    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final ProductImageStorage productImageStorage;

    public UploadProductImageUseCase(ProductImageStorage productImageStorage) {
        this.productImageStorage = productImageStorage;
    }

    public StoredProductImage execute(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("La imagen es obligatoria");
        }
        if (content.length > MAX_IMAGE_BYTES) {
            throw new PayloadTooLargeException("La imagen supera el limite de 5 MB");
        }

        String detectedContentType = detectContentType(content);
        if (detectedContentType == null) {
            throw new IllegalArgumentException("La imagen debe ser JPEG, PNG o WebP");
        }
        if (declaredContentType == null || !detectedContentType.equalsIgnoreCase(declaredContentType)) {
            throw new IllegalArgumentException("El Content-Type no coincide con el contenido de la imagen");
        }

        return productImageStorage.store(
                content,
                detectedContentType,
                EXTENSIONS.get(detectedContentType)
        );
    }

    private String detectContentType(byte[] content) {
        if (isJpeg(content)) {
            return "image/jpeg";
        }
        if (isPng(content)) {
            return "image/png";
        }
        if (isWebp(content)) {
            return "image/webp";
        }
        return null;
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && unsigned(content[0]) == 0xFF
                && unsigned(content[1]) == 0xD8
                && unsigned(content[2]) == 0xFF;
    }

    private boolean isPng(byte[] content) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(content[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && matchesAscii(content, 0, "RIFF")
                && matchesAscii(content, 8, "WEBP");
    }

    private boolean matchesAscii(byte[] content, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (content[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }
}

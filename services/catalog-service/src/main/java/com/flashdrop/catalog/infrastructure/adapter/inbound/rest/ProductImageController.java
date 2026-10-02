package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.flashdrop.catalog.application.storage.ProductImageContent;
import com.flashdrop.catalog.application.usecase.GetProductImageUseCase;
import com.flashdrop.catalog.application.usecase.UploadProductImageUseCase;
import com.flashdrop.catalog.domain.exception.ImageStorageException;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.ProductImageUploadResponse;

@RestController
@RequestMapping
public class ProductImageController {

    private final UploadProductImageUseCase uploadProductImageUseCase;
    private final GetProductImageUseCase getProductImageUseCase;

    public ProductImageController(
            UploadProductImageUseCase uploadProductImageUseCase,
            GetProductImageUseCase getProductImageUseCase
    ) {
        this.uploadProductImageUseCase = uploadProductImageUseCase;
        this.getProductImageUseCase = getProductImageUseCase;
    }

    @PostMapping(
            value = "/api/catalog/my/products/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageUploadResponse upload(@RequestPart("file") MultipartFile file) {
        try {
            return ProductImageUploadResponse.from(
                    uploadProductImageUseCase.execute(file.getBytes(), file.getContentType())
            );
        } catch (IOException exception) {
            throw new ImageStorageException("No se pudo leer la imagen enviada", exception);
        }
    }

    @GetMapping("/catalog/images/products/{year}/{month}/{filename:.+}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String filename
    ) {
        ProductImageContent image = getProductImageUseCase.execute(
                "products/%s/%s/%s".formatted(year, month, filename)
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(image.content());
    }
}

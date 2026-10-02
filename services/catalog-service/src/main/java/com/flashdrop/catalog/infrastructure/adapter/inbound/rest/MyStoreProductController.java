package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.OwnerCreateProductUseCase;
import com.flashdrop.catalog.application.usecase.OwnerDeleteProductUseCase;
import com.flashdrop.catalog.application.usecase.OwnerListProductsUseCase;
import com.flashdrop.catalog.application.usecase.OwnerUpdateProductUseCase;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.OwnerCreateProductRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.OwnerUpdateProductRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.ProductResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/catalog/my/products")
public class MyStoreProductController {

    private final OwnerCreateProductUseCase ownerCreateProductUseCase;
    private final OwnerListProductsUseCase ownerListProductsUseCase;
    private final OwnerUpdateProductUseCase ownerUpdateProductUseCase;
    private final OwnerDeleteProductUseCase ownerDeleteProductUseCase;
    private final ProductImageUrlResolver productImageUrlResolver;

    public MyStoreProductController(
            OwnerCreateProductUseCase ownerCreateProductUseCase,
            OwnerListProductsUseCase ownerListProductsUseCase,
            OwnerUpdateProductUseCase ownerUpdateProductUseCase,
            OwnerDeleteProductUseCase ownerDeleteProductUseCase,
            ProductImageUrlResolver productImageUrlResolver
    ) {
        this.ownerCreateProductUseCase = ownerCreateProductUseCase;
        this.ownerListProductsUseCase = ownerListProductsUseCase;
        this.ownerUpdateProductUseCase = ownerUpdateProductUseCase;
        this.ownerDeleteProductUseCase = ownerDeleteProductUseCase;
        this.productImageUrlResolver = productImageUrlResolver;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OwnerCreateProductRequest request
    ) {
        Product product = ownerCreateProductUseCase.execute(userId(jwt), request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @GetMapping
    public List<ProductResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return ownerListProductsUseCase.execute(userId(jwt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{productId}")
    public ProductResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId,
            @Valid @RequestBody OwnerUpdateProductRequest request
    ) {
        return toResponse(ownerUpdateProductUseCase.execute(
                userId(jwt),
                productId,
                request.toCommand()
        ));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId
    ) {
        ownerDeleteProductUseCase.execute(userId(jwt), productId);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new OwnershipAccessDeniedException("El identificador del usuario autenticado no es valido");
        }
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.fromDomain(
                product,
                productImageUrlResolver.resolve(product.getImage())
        );
    }
}

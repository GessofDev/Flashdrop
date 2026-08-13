package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.GetProductsByIdsUseCase;
import com.flashdrop.catalog.application.usecase.ListProductsUseCase;
import com.flashdrop.catalog.application.usecase.CreateProductUseCase;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.CreateProductRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.ProductResponse;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.ValidateProductsRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.ValidateProductsResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/catalog/products")
public class ProductController {

    // El controller recibe HTTP y delega la logica a use cases.
    // No consulta Supabase directamente.
    private final ListProductsUseCase listProductsUseCase;
    private final GetProductsByIdsUseCase getProductsByIdsUseCase;
    private final CreateProductUseCase createProductUseCase;

    public ProductController(
            ListProductsUseCase listProductsUseCase,
            GetProductsByIdsUseCase getProductsByIdsUseCase,
            CreateProductUseCase createProductUseCase
    ) {
        this.listProductsUseCase = listProductsUseCase;
        this.getProductsByIdsUseCase = getProductsByIdsUseCase;
        this.createProductUseCase = createProductUseCase;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        // Convertimos el JSON que llega por HTTP a un Product del dominio.
        Product product = new Product(
                null,
                request.categoryId(),
                request.restaurantId(),
                request.name(),
                request.description(),
                new Money(request.price()),
                request.image(),
                request.available() == null || request.available()
        );

        // El use case guarda el producto y el DTO deja lista la respuesta JSON.
        ProductResponse response = ProductResponse.fromDomain(createProductUseCase.execute(product));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ProductResponse> listProducts() {
        // GET /catalog/products: lista productos y los transforma a respuesta publica.
        return listProductsUseCase.execute()
                .stream()
                .map(ProductResponse::fromDomain)
                .toList();
    }

    @PostMapping("/validate")
    public ValidateProductsResponse validateProducts(@Valid @RequestBody ValidateProductsRequest request) {
        // POST /catalog/products/validate: confirma si una lista de ids existe.
        // Esto sirve para que otra logica, como pedidos, valide productos antes de comprar.
        List<Long> requestedIds = request.productIds() == null ? List.of() : request.productIds();
        List<Product> products = getProductsByIdsUseCase.execute(requestedIds);
        List<Long> foundIds = products.stream().map(Product::getId).toList();
        List<Long> missingIds = requestedIds.stream()
                .filter(productId -> !foundIds.contains(productId))
                .toList();

        return new ValidateProductsResponse(
                missingIds.isEmpty(),
                products.stream().map(ProductResponse::fromDomain).toList(),
                missingIds
        );
    }
}

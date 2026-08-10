package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.ListCategoriesUseCase;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.CategoryResponse;

@RestController
@RequestMapping("/catalog/categories")
public class CategoryController {

    // Entrada HTTP para categorias. La logica real queda en el use case.
    private final ListCategoriesUseCase listCategoriesUseCase;

    public CategoryController(ListCategoriesUseCase listCategoriesUseCase) {
        this.listCategoriesUseCase = listCategoriesUseCase;
    }

    @GetMapping
    public List<CategoryResponse> listCategories() {
        // GET /catalog/categories: devuelve categorias listas para la app o admin.
        return listCategoriesUseCase.execute()
                .stream()
                .map(CategoryResponse::fromDomain)
                .toList();
    }
}

package com.flashdrop.catalog.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.CategoryRepositoryPort;
import com.flashdrop.catalog.domain.model.Category;

@Service
public class ListCategoriesUseCase {

    // Contrato que permite traer categorias desde memoria o Supabase.
    private final CategoryRepositoryPort categoryRepositoryPort;

    public ListCategoriesUseCase(CategoryRepositoryPort categoryRepositoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
    }

    public List<Category> execute() {
        // Accion del sistema: listar categorias para filtros o formularios.
        return categoryRepositoryPort.findAll();
    }
}

package com.flashdrop.catalog.application.port.outbound;

import java.util.List;

import com.flashdrop.catalog.domain.model.Category;

// Puerto para categorias: define lo que la logica necesita, no como se obtiene.
public interface CategoryRepositoryPort {

    List<Category> findAll();
}

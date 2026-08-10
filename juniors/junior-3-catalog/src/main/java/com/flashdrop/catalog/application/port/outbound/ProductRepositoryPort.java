package com.flashdrop.catalog.application.port.outbound;

import java.util.List;

import com.flashdrop.catalog.domain.model.Product;

// Puerto de salida: la aplicacion declara que necesita leer/guardar productos.
// No importa si la implementacion real usa Supabase, memoria, MySQL o Postgres.
public interface ProductRepositoryPort {

    // Trae todos los productos del origen de datos activo.
    List<Product> findAll();

    // Busca solo los productos cuyos ids llegaron desde otra logica, por ejemplo pedidos.
    List<Product> findByIds(List<Long> ids);

    // Guarda un producto y devuelve el producto creado con su id real.
    Product save(Product product);
}

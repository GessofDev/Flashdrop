package com.flashdrop.catalog.application.port.outbound;

import java.util.List;
import java.util.Optional;

import com.flashdrop.catalog.domain.model.Product;

// Puerto de salida: la aplicacion declara que necesita leer/guardar productos.
// No importa si la implementacion real usa Supabase, memoria, MySQL o Postgres.
public interface ProductRepositoryPort {

    // Trae todos los productos del origen de datos activo.
    List<Product> findAll();

    // Busca solo los productos cuyos ids llegaron desde otra logica, por ejemplo pedidos.
    List<Product> findByIds(List<Long> ids);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByRestaurantId(Long restaurantId);

    // Busca un producto puntual para operaciones internas de actualizacion.
    Optional<Product> findById(Long id);

    // Guarda un producto y devuelve el producto creado con su id real.
    Product save(Product product);

    // Actualiza un producto existente y devuelve su estado final.
    Product update(Product product);
}

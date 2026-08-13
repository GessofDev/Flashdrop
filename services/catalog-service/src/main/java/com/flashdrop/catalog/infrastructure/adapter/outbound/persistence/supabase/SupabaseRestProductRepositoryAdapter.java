package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.supabase;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;
import com.flashdrop.catalog.infrastructure.config.EnvironmentValues;

@Repository
@Profile("supabase")
public class SupabaseRestProductRepositoryAdapter implements ProductRepositoryPort {

    // Columnas que pedimos a Supabase. Usamos nombres reales de la tabla.
    private static final String PRODUCT_SELECT = "id,category_id,restaurant_id,name,description,price,image,is_available";

    private final RestClient restClient;
    private final String supabaseUrl;

    public SupabaseRestProductRepositoryAdapter() {
        // Lee las credenciales desde .env. La service role key debe vivir solo en backend.
        String serviceRoleKey = EnvironmentValues.required("SUPABASE_SERVICE_ROLE_KEY");
        this.supabaseUrl = EnvironmentValues.required("SUPABASE_URL").replaceAll("/+$", "");
        this.restClient = RestClient.builder()
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    @Override
    public List<Product> findAll() {
        // GET a /rest/v1/products usando la API REST de Supabase.
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/products")
                .queryParam("select", PRODUCT_SELECT)
                .queryParam("order", "id.desc")
                .build()
                .toUriString();

        return fetchProducts(url);
    }

    @Override
    public List<Product> findByIds(List<Long> ids) {
        // Si otra logica manda ids, construimos el filtro id=in.(1,2,3).
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        String idFilter = "in.(" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")";
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/products")
                .queryParam("select", PRODUCT_SELECT)
                .queryParam("id", idFilter)
                .build()
                .toUriString();

        return fetchProducts(url);
    }

    @Override
    public Product save(Product product) {
        // POST a /rest/v1/products: aqui ocurre el insert real en Supabase.
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/products")
                .queryParam("select", PRODUCT_SELECT)
                .build()
                .toUriString();

        ProductInsertRequest request = new ProductInsertRequest(
                product.getCategoryId(),
                product.getRestaurantId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().amount(),
                product.getImage(),
                product.isAvailable()
        );

        ProductRow[] rows = restClient.post()
                .uri(url)
                .header("Prefer", "return=representation")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProductRow[].class);

        if (rows == null || rows.length == 0) {
            throw new IllegalStateException("Supabase no devolvio el producto creado");
        }

        return rows[0].toDomain();
    }

    private List<Product> fetchProducts(String url) {
        // Ejecuta el GET y convierte cada fila JSON a un Product del dominio.
        ProductRow[] rows = restClient.get()
                .uri(url)
                .retrieve()
                .body(ProductRow[].class);

        if (rows == null) {
            return List.of();
        }

        return Arrays.stream(rows)
                .map(ProductRow::toDomain)
                .toList();
    }

    private record ProductRow(
            Long id,
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("restaurant_id") Long restaurantId,
            String name,
            String description,
            BigDecimal price,
            String image,
            @JsonProperty("is_available") boolean available
    ) {
        // Fila como viene desde Supabase -> modelo interno de la aplicacion.
        Product toDomain() {
            return new Product(
                    id,
                    categoryId,
                    restaurantId,
                    name,
                    description,
                    new Money(price),
                    image,
                    available
            );
        }
    }

    private record ProductInsertRequest(
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("restaurant_id") Long restaurantId,
            String name,
            String description,
            BigDecimal price,
            String image,
            @JsonProperty("is_available") boolean available
    ) {
        // JSON que se envia a Supabase para crear un producto.
    }
}

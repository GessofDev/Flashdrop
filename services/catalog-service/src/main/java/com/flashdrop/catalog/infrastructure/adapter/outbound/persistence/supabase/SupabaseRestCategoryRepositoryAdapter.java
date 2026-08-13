package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.supabase;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.flashdrop.catalog.application.port.outbound.CategoryRepositoryPort;
import com.flashdrop.catalog.domain.model.Category;
import com.flashdrop.catalog.infrastructure.config.EnvironmentValues;

@Repository
@Profile("supabase")
public class SupabaseRestCategoryRepositoryAdapter implements CategoryRepositoryPort {

    // Adapter de salida: implementa el port consultando categorias en Supabase.
    private final RestClient restClient;
    private final String supabaseUrl;

    public SupabaseRestCategoryRepositoryAdapter() {
        String serviceRoleKey = EnvironmentValues.required("SUPABASE_SERVICE_ROLE_KEY");
        this.supabaseUrl = EnvironmentValues.required("SUPABASE_URL").replaceAll("/+$", "");
        this.restClient = RestClient.builder()
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    @Override
    public List<Category> findAll() {
        // GET /rest/v1/categories usando la API REST de Supabase.
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/categories")
                .queryParam("select", "id,name,description,image")
                .queryParam("order", "name.asc")
                .build()
                .toUriString();

        CategoryRow[] rows = restClient.get()
                .uri(url)
                .retrieve()
                .body(CategoryRow[].class);

        if (rows == null) {
            return List.of();
        }

        return Arrays.stream(rows)
                .map(CategoryRow::toDomain)
                .toList();
    }

    private record CategoryRow(Long id, String name, String description, String image) {
        // Convierte una fila JSON de Supabase al modelo Category.
        Category toDomain() {
            return new Category(id, name, description, image);
        }
    }
}

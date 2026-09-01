package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.supabase;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.infrastructure.config.EnvironmentValues;

@Repository
@Profile("supabase")
public class SupabaseRestRestaurantRepositoryAdapter implements RestaurantRepositoryPort {

    // Adapter de salida: implementa el port consultando restaurantes en Supabase.
    private static final String RESTAURANT_SELECT = "id,user_id,name,address,created_at";

    private final RestClient restClient;
    private final String supabaseUrl;

    public SupabaseRestRestaurantRepositoryAdapter() {
        String serviceRoleKey = EnvironmentValues.required("SUPABASE_SERVICE_ROLE_KEY");
        this.supabaseUrl = EnvironmentValues.required("SUPABASE_URL").replaceAll("/+$", "");
        this.restClient = RestClient.builder()
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    @Override
    public List<Restaurant> findAll() {
        // La tabla se llama restaurant en la base actual.
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/restaurant")
                .queryParam("select", RESTAURANT_SELECT)
                .queryParam("order", "name.asc")
                .build()
                .toUriString();

        return fetchRestaurants(url).stream()
                .map(RestaurantRow::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public Optional<Restaurant> findById(Long id) {
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/restaurant")
                .queryParam("select", RESTAURANT_SELECT)
                .queryParam("id", "eq." + id)
                .queryParam("limit", 1)
                .build()
                .toUriString();

        return fetchRestaurants(url).stream()
                .findFirst()
                .map(RestaurantRow::toDomain);
    }

    @Override
    public Optional<Restaurant> findByUserId(Long userId) {
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/restaurant")
                .queryParam("select", RESTAURANT_SELECT)
                .queryParam("user_id", "eq." + userId)
                .queryParam("limit", 1)
                .build()
                .toUriString();

        return fetchRestaurants(url).stream()
                .findFirst()
                .map(RestaurantRow::toDomain);
    }

    private List<RestaurantRow> fetchRestaurants(String url) {
        RestaurantRow[] rows = restClient.get()
                .uri(url)
                .retrieve()
                .body(RestaurantRow[].class);

        if (rows == null) {
            return List.of();
        }

        return Arrays.asList(rows);
    }

    private record RestaurantRow(
            Long id,
            @JsonProperty("user_id") Long userId,
            String name,
            String address,
            @JsonProperty("created_at") OffsetDateTime createdAt
    ) {
        // Convierte una fila JSON de Supabase al modelo Restaurant.
        Restaurant toDomain() {
            return new Restaurant(
                    id,
                    userId,
                    name,
                    address,
                    createdAt == null ? null : createdAt.toLocalDateTime()
            );
        }
    }
}

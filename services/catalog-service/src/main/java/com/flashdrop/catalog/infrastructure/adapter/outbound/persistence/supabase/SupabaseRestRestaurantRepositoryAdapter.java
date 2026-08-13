package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.supabase;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

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
                .queryParam("select", "id,user_id,name,address,created_at")
                .queryParam("order", "name.asc")
                .build()
                .toUriString();

        RestaurantRow[] rows = restClient.get()
                .uri(url)
                .retrieve()
                .body(RestaurantRow[].class);

        if (rows == null) {
            return List.of();
        }

        return Arrays.stream(rows)
                .map(RestaurantRow::toDomain)
                .toList();
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

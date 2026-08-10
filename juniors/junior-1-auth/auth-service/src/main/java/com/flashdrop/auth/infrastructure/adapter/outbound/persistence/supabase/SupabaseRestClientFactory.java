package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Builds a {@link RestClient} pre-configured to talk to the Supabase REST API.
 *
 * <p>The default {@code RestClient.builder()} uses a Jackson {@link ObjectMapper}
 * without {@link JavaTimeModule}, which causes {@link java.time.Instant} values
 * to be serialised as epoch seconds (e.g. {@code "1787883920.830231029"}).
 * PostgreSQL's {@code timestamp with time zone} columns reject that format and
 * respond with SQLSTATE {@code 22007}. This factory registers the JSR-310
 * module and disables {@code WRITE_DATES_AS_TIMESTAMPS} so timestamps are
 * serialised as ISO-8601 strings, matching what Supabase expects.
 *
 * <p>The mapper is also configured with {@code FAIL_ON_UNKNOWN_PROPERTIES=false}
 * so that extra columns Supabase may return (e.g. {@code created_at},
 * {@code updated_at}, audit timestamps) do not break deserialisation when a
 * DTO has not been kept perfectly in sync with the schema.
 */
public final class SupabaseRestClientFactory {

    private SupabaseRestClientFactory() {}

    public static RestClient create(String baseUrl, String serviceRoleKey) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);

        return RestClient.builder()
                .baseUrl(baseUrl + "/rest/v1")
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .defaultHeader("Accept", "application/json")
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(jsonConverter);
                })
                .build();
    }
}
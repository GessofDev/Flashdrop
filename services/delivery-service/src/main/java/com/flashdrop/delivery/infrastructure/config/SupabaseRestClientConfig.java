package com.flashdrop.delivery.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class SupabaseRestClientConfig {

    @Bean
    public RestClient supabaseRestClient(
            @Value("${supabase.url}") String url,
            @Value("${supabase.service-role-key}") String key) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(mapper);
        jsonConverter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.valueOf("application/vnd.pgrst.object+json")
        ));

        return RestClient.builder()
                .baseUrl(url + "/rest/v1")
                .defaultHeader("apikey", key)
                .defaultHeader("Authorization", "Bearer " + key)
                .defaultHeader("Accept", "application/json")
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(jsonConverter);
                })
                .build();
    }
}
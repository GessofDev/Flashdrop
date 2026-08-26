package com.flashdrop.catalog.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class EnvironmentValues {

    private static final String DEFAULT_SUPABASE_URL = "http://supabasekong-wymwq8rktid7ov678oe4va90.76.13.169.150.sslip.io";

    private EnvironmentValues() {
    }

    public static String required(String key) {
        return find(key)
                .orElseThrow(() -> new IllegalStateException("Falta configurar " + key));
    }

    private static Optional<String> find(String key) {
        String systemValue = System.getenv(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return Optional.of(systemValue.trim());
        }

        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            return fallback(key);
        }

        try {
            return Files.readAllLines(envPath)
                    .stream()
                    .map(line -> line.replace("\uFEFF", "").trim())
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .filter(line -> line.startsWith(key + "="))
                    .map(line -> line.substring((key + "=").length()).trim())
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .or(() -> fallback(key));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el archivo .env", exception);
        }
    }

    private static Optional<String> fallback(String key) {
        if ("SUPABASE_URL".equals(key)) {
            return Optional.of(DEFAULT_SUPABASE_URL);
        }

        return Optional.empty();
    }
}

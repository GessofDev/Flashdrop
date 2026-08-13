package com.flashdrop.catalog.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class EnvironmentValues {

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
            return Optional.empty();
        }

        try {
            return Files.readAllLines(envPath)
                    .stream()
                    .map(line -> line.replace("\uFEFF", "").trim())
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .filter(line -> line.startsWith(key + "="))
                    .map(line -> line.substring((key + "=").length()).trim())
                    .filter(value -> !value.isBlank())
                    .findFirst();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el archivo .env", exception);
        }
    }
}

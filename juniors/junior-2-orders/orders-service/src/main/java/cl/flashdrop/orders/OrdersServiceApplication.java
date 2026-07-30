package cl.flashdrop.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * FlashDrop - Orders Service
 *
 * Microservicio de gestión de pedidos implementado con Arquitectura Hexagonal (Ports & Adapters).
 * Responsable del ciclo de vida completo de los pedidos: creación, seguimiento,
 * asignación de repartidores y actualización de estados.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrdersServiceApplication {

    public static void main(String[] args) {
        // Cargar archivo .env local si existe en el directorio de trabajo
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.exists(envPath)) {
                java.nio.file.Files.lines(envPath)
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> {
                            int eqIdx = line.indexOf('=');
                            if (eqIdx > 0) {
                                String key = line.substring(0, eqIdx).trim();
                                String value = line.substring(eqIdx + 1).trim();
                                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) ||
                                    (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                                    value = value.substring(1, value.length() - 1);
                                }
                                System.setProperty(key, value);
                            }
                        });
            }
        } catch (Exception e) {
            System.err.println("Error cargando el archivo .env: " + e.getMessage());
        }

        SpringApplication.run(OrdersServiceApplication.class, args);
    }
}

package cl.flashdrop.orders.infrastructure.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador de health check.
 *
 * Expone el endpoint {@code GET /health} requerido por el gateway SafeGateway
 * para verificar que el servicio está operativo.
 *
 * Respuesta esperada: {@code {"service": "orders-service", "status": "ok"}}
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "service", "orders-service",
            "status", "ok"
        );
    }
}

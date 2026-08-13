package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "service", "auth-service",
            "status", "ok"
        );
    }
}

# Plan de Integracion — auth-service (nikohomie)

## Contexto

Tu microservicio es el emisor de tokens JWT.
La base de datos **ya existe en Supabase** con todas las tablas creadas.
**Ningun servicio debe crear tablas en esta etapa** — solo conectarse y usarlas.

## Cambios Requeridos

### 1. Puerto: sin cambios

Tu servicio ya corre en el puerto **8081**, que es el correcto.
No necesitas modificar nada.

### 2. Flyway: DESHABILITAR

**La base de datos ya existe en Supabase.** No necesitas crear tablas.
Deshabilita Flyway para evitar conflictos.

**Archivo:** `src/main/resources/application.yml`

```diff
  flyway:
-   enabled: true
+   enabled: false
```

**Eliminar o comentar las lineas de Flyway:**

```diff
- spring.flyway.locations=classpath:db/migration
- spring.flyway.baseline-on-migrate=true
- spring.flyway.baseline-version=0
```

> **Nota:** En el futuro, cuando leas la mision de crear tus propias migraciones,
> habilitaras Flyway y crearas las migraciones de tu bounded context.

### 3. CORS: habilitar para el gateway

El gateway (SafeGateway) va a proxyar requests hacia tu servicio.
Necesitas aceptar requests del origen del gateway.

**Archivo:** `src/main/resources/application.yml`

Agregar o verificar que exista la configuracion de CORS:

```yaml
server:
  port: ${PORT:8081}
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never
```

**Archivo:** Crear o modificar `src/main/java/com/flashdrop/auth/config/CorsConfig.java`

```java
package com.flashdrop.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",   // Gateway local
            "http://localhost:5173",   // Frontend local (Vite)
            "http://localhost:4200"    // Frontend local (Angular)
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

### 4. Health endpoint: verificar

El gateway consulta `/health` en cada servicio.
Verifica que tu servicio responda en `/health`.

**Archivo:** Crear `src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/HealthController.java`

```java
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
```

### 5. Actuator health: opcional pero recomendado

Si ya tenes Spring Boot Actuator, asegurate de que `/health` este expuesto.

**Verificar en `application.yml`:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
```

### 6. JWKS endpoint: verificar

El gateway valida JWT usando tu clave publica.
Asegurate de que el endpoint JWKS este accesible.

**Verificar que exista `JwksController.java`** y que devuelva la clave publica en formato JWKS.

## Resumen de cambios

| Archivo | Cambio | Prioridad |
|---|---|---|
| `application.yml` | Flyway enabled → false | 🔴 Critica |
| `CorsConfig.java` | Crear para aceptar requests del gateway | Alta |
| `HealthController.java` | Crear si no existe | Alta |
| `JwksController.java` | Verificar que funcione | Alta |

## Para probar

```bash
# 1. Levantar el servicio
./gradlew :auth-service:bootRun

# 2. Verificar health
curl http://localhost:8081/health
# Respuesta esperada: {"service":"auth-service","status":"ok"}

# 3. Verificar JWKS
curl http://localhost:8081/.well-known/jwks.json
# Respuesta esperada: JSON con la clave publica

# 4. Probar login
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"test@test.com","password":"123456"}'
```

## Notas importantes

- **No modifiques el puerto** (debe ser 8081)
- **Deshabilita Flyway** (la BD ya existe en Supabase)
- **No crees tablas** (en esta etapa solo conectamos y usamos)
- El gateway va a enviar `X-Forwarded-For` con la IP real del cliente
- El JWT que emites debe ser compatible con RS256 para que el gateway lo valide
- **Mision futura:** creare las migraciones de mi bounded context

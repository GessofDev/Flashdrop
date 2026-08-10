# Plan de Integracion — catalog-service (javier-sudo)

## Contexto

Tu microservicio maneja productos, categorias y restaurantes.
Ya tenes arquitectura hexagonal, 3 perfiles de persistencia, y buena documentacion.
La base de datos **ya existe en Supabase** con todas las tablas creadas.
**Ningun servicio debe crear tablas en esta etapa** — solo conectarse y usarlas.

Los cambios necesarios son minimos.

## Cambios Requeridos

### 1. Puerto: cambiar a 8082

**Archivo:** `src/main/resources/application.yaml` o `src/main/resources/application-supabase.yaml`

Si no tenes puerto configurado, agregar:

```yaml
server:
  port: 8082
```

Si ya lo tenes en otro puerto, cambiarlo a **8082**.

### 2. Flyway: deshabilitado ✅

**La base de datos ya existe en Supabase.** No necesitas crear tablas.

Tu servicio ya tiene Flyway deshabilitado en los perfiles `local` y `supabase`:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
```

**No necesitas cambiar nada.** Esto es correcto porque auth-service es el dueño de las migraciones.

### 3. CORS: habilitar para el gateway

**Archivo:** Crear `src/main/java/com/flashdrop/catalog/config/CorsConfig.java`

```java
package com.flashdrop.catalog.config;

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

### 4. Health endpoint: ya existe ✅

Ya tenes un `HealthController.java` que responde en `/health`:

```java
@GetMapping("/health")
public Map<String, String> health() {
    return Map.of(
        "service", "catalog-service",
        "status", "ok"
    );
}
```

**No necesitas cambiar nada.**

### 5. Perfil Supabase: verificar que funciona

Tu servicio tiene 3 perfiles:
- `local` — datos en memoria (para desarrollo rapido)
- `supabase` — usa la API REST de Supabase
- `postgres` — JPA directo a PostgreSQL

**Para integracion con el gateway, usar el perfil `supabase`:**

```bash
.\gradlew.bat bootRun --args="--spring.profiles.active=supabase"
```

**Verificar que las variables de entorno esten configuradas:**

```bash
export SUPABASE_URL=tu_url_de_supabase
export SUPABASE_SERVICE_ROLE_KEY=tu_service_role_key
```

### 6. IDs: verificar tipo

auth-service usa **UUID** para todos los IDs.
Si tu catalog-service usa Long/BigInt para IDs de productos/categorias/restaurantes, hay un conflicto.

**Verificar en tu dominio:**

```java
// Si es UUID (correcto):
public class Product {
    private UUID id;
    // ...
}

// Si es Long (necesita cambio):
public class Product {
    private Long id;  // ← Cambiar a UUID
    // ...
}
```

Si tus IDs son Long, necesitaras cambiarlos a UUID para que coincidan con el schema de auth-service.

## Resumen de cambios

| Archivo | Cambio | Prioridad |
|---|---|---|
| `application.yaml` | Puerto → 8082 | Alta |
| `CorsConfig.java` | Crear para aceptar requests del gateway | Alta |
| `HealthController.java` | Ya existe ✅ | — |
| Flyway | Ya deshabilitado ✅ | — |
| IDs de dominio | Verificar que sean UUID | Alta |

## Para probar

```bash
# 1. Levantar con perfil supabase
.\gradlew.bat bootRun --args="--spring.profiles.active=supabase"

# 2. Verificar health
curl http://localhost:8082/health
# Respuesta esperada: {"service":"catalog-service","status":"ok"}

# 3. Verificar productos
curl http://localhost:8082/catalog/products
# Respuesta esperada: lista de productos

# 4. Verificar categorias
curl http://localhost:8082/catalog/categories
# Respuesta esperada: lista de categorias

# 5. Verificar restaurantes
curl http://localhost:8082/catalog/restaurants
# Respuesta esperada: lista de restaurantes

# 6. Probar validacion de productos
curl -X POST http://localhost:8082/catalog/products/validate \
  -H "Content-Type: application/json" \
  -d '{"productIds": [1, 2, 999]}'
# Respuesta esperada: productos encontrados + IDs faltantes
```

## Notas importantes

- **Puerto 8082** es el correcto para tu servicio
- **Flyway deshabilitado** es correcto (la BD ya existe en Supabase)
- **No crees tablas** (en esta etapa solo conectamos y usamos)
- **Perfil supabase** es el recomendado para integracion
- Tu documentacion (GUIA_LECTURA) es excelente — los demas juniors pueden aprender de ella
- La arquitectura hexagonal que implementaste es la correcta: Controller → UseCase → Port → Adapter
- **Mision futura:** creare las migraciones de mi bounded context

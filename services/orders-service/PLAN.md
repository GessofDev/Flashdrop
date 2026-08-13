# Plan de Integracion — orders-service (felipepousacerda)

## Contexto

Tu microservicio maneja pedidos y delivery.
La base de datos **ya existe en Supabase** con todas las tablas creadas.
**Ningun servicio debe crear tablas en esta etapa** — solo conectarse y usarlas.

Hay **3 cambios criticos** que hacer antes de poder integrarlo con el gateway y los demás servicios.

## Cambios Requeridos

### 1. Puerto: cambiar de 8082 a 8083

**Problema:** Tu servicio usa el puerto 8082, que colisiona con catalog-service de nikohomie.

**Archivo:** `src/main/resources/application.properties`

```diff
- server.port=8082
+ server.port=8083
```

### 2. Flyway: deshabilitar

**La base de datos ya existe en Supabase.** No necesitas crear tablas.
Si tu servicio corre Flyway, va a intentar crear tablas duplicadas y fallar.

**Archivo:** `src/main/resources/application.properties`

```diff
- spring.flyway.enabled=true
+ spring.flyway.enabled=false
```

**Eliminar o comentar las lineas de Flyway:**

```diff
- spring.flyway.locations=classpath:db/migration
- spring.flyway.baseline-on-migrate=true
```

### 3. Base de datos: apuntar a la misma DB

**Problema:** Tu servicio apunta a `flashdrop_delivery`, pero el schema autoritativo esta en `flashdrop` (la misma que usa auth-service).

**Archivo:** `src/main/resources/application.properties`

```diff
- spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/flash_drop_delivery}
+ spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/flashdrop}
```

### 4. CORS: habilitar para el gateway

**Archivo:** Crear `src/main/java/cl/flashdrop/orders/config/CorsConfig.java`

```java
package cl.flashdrop.orders.config;

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

### 5. Health endpoint: verificar

El gateway consulta `/health` en cada servicio.

**Verificar que exista un endpoint `/health`** que devuelva algo como:

```json
{
  "service": "orders-service",
  "status": "ok"
}
```

Si no existe, crear `src/main/java/cl/flashdrop/orders/infrastructure/api/HealthController.java`:

```java
package cl.flashdrop.orders.infrastructure.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
```

### 6. RabbitMQ: hacer opcional (recomendado)

Tu servicio usa RabbitMQ para eventos asincronos. Si RabbitMQ no esta corriendo, el servicio puede fallar al arrancar.

**Opcion A:** Hacer que RabbitMQ sea opcional en `application.properties`:

```properties
# RabbitMQ - opcional
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:guest}
```

**Opcion B:** Levantar RabbitMQ con Docker:

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

### 7. Base de datos: verificar migracion

auth-service ya creo las tablas necesarias en V4:
- `orders`
- `order_items`
- `delivery_routes`

Tu servicio debe usar estas tablas, no crear las suyas.

**Verificar que tus entidades JPA apunten a las tablas correctas:**
- `OrderEntity` → tabla `orders`
- `OrderItemEntity` → tabla `order_items`
- `DeliveryRouteEntity` → tabla `delivery_routes`

Si tus entidades tienen nombres de columnas diferentes a las de la migracion V4, necesitaras ajustar las anotaciones `@Column`.

## Resumen de cambios

| Archivo | Cambio | Prioridad |
|---|---|---|
| `application.properties` | Puerto 8082 → 8083 | 🔴 Critica |
| `application.properties` | Flyway enabled → false | 🔴 Critica |
| `application.properties` | DB URL → flashdrop | 🔴 Critica |
| `CorsConfig.java` | Crear para aceptar requests del gateway | Alta |
| `HealthController.java` | Crear si no existe | Alta |
| RabbitMQ | Hacer opcional o levantar container | Media |

## Para probar

```bash
# 1. Levantar el servicio
mvn spring-boot:run

# 2. Verificar health
curl http://localhost:8083/health
# Respuesta esperada: {"service":"orders-service","status":"ok"}

# 3. Verificar que la DB esta conectada
curl http://localhost:8083/api/orders
# Respuesta esperada: lista de pedidos (o array vacio)

# 4. Probar crear un pedido
curl -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "address": "Av. Providencia 1000",
    "paymentMethod": "Efectivo",
    "items": [{"productId": 1, "quantity": 2}]
  }'
```

## Notas importantes

- **Cambia el puerto a 8083** (8082 esta ocupado por catalog)
- **Deshabilita Flyway** (auth-service ya creo las tablas)
- **Apunta a la DB `flashdrop`** (misma que auth-service)
- Los IDs en la DB son **UUID**, no Long/BigInt — si tus entidades usan Long, necesitaras cambiarlas a UUID
- El delivery fee hardcodeado es 2500 CLP (configurable via `orders.delivery-fee`)

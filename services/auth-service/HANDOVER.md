# Estado Actual de la Arquitectura (Handover)

Este documento describe el estado actual del monorepo `flashdrop-microservices`, las decisiones arquitectónicas tomadas y los pasos pendientes para que el equipo pueda continuar con la integración (API Gateway, Orders Service, Delivery Service).

---

## 🏗️ 1. Estado Actual (Lo que ya está construido)

Hemos migrado y unificado la arquitectura hacia un modelo **Monorepo Multimódulo (Gradle)** utilizando **Spring Boot 3** y **Java 21**. 

Actualmente, el sistema consta de:

1. **`shared-observability`** (Librería Core):
   - Provee logs estructurados en JSON (Logback).
   - Inyecta `trace_id` automático en todas las peticiones para trazabilidad.
   - Provee el modelo `ApiError` estandarizado para que todos los microservicios devuelvan el mismo formato de error.

2. **`auth-service`** (Puerto 8081):
   - Responsable de Login, Registro y validación de usuarios.
   - **Dueño de las Migraciones (Flyway):** Es el único servicio que ejecuta scripts SQL (`src/main/resources/db/migration`). Las tablas de *todos* los servicios se crean desde aquí.
   - Emite **JWT asimétricos (RS256)**. Expone su Clave Pública para que los otros servicios puedan validar los tokens sin tener que hacer llamadas HTTP de vuelta al auth-service.

3. **`catalog-service`** (Puerto 8082):
   - Responsable de Productos, Categorías y Restaurantes.
   - Sigue arquitectura Hexagonal/DDD (Domain, Application, Infrastructure).
   - **Seguridad Autónoma:** No valida tokens llamando al auth-service. Usa la Clave Pública RSA para validar las firmas JWT localmente.
   - Operaciones GET son públicas. POST/PUT/DELETE requieren JWT válido.

4. **Infraestructura Base:**
   - `docker-compose.yml` configurado para levantar PostgreSQL local.
   - Archivo `.env.example` listo para que los desarrolladores clonen y configuren su entorno.

### 🔑 Decisiones Técnicas Críticas
- **Tipos de ID:** Todo el sistema ha sido estandarizado para usar **UUID** (gen_random_uuid() en Postgres). No se usan Long/BigInt autoincrementales por motivos de seguridad y para facilitar sistemas distribuidos.
- **Validaciones Defensivas:** Todos los DTOs usan Jakarta Validation (`@NotNull`, `@NotBlank`, `@Positive`) y rechazan payloads inválidos antes de tocar el dominio.

---

## 🚧 2. Lo que Falta (Roadmap para el Equipo)

### A. Integrar el API Gateway (Asignado al compañero)
Dado que los microservicios corren en puertos distintos (8081, 8082), el frontend no debería hablar directamente con ellos para evitar problemas de CORS y manejo de múltiples URLs.

**Pasos para integrar el Gateway externo/existente:**
1. Crear el módulo `api-gateway/` en este monorepo (si es código Spring Cloud Gateway) o configurarlo en un repo separado.
2. Configurar el enrutamiento:
   - `/auth/**`  → routear a `localhost:8081`
   - `/catalog/**` → routear a `localhost:8082`
   - `/orders/**` → routear a `localhost:8083` (futuro)
3. **CORS:** El Gateway debe ser el único encargado de configurar y aceptar CORS globales. Los microservicios internos deben confiar en el Gateway.
4. (Opcional) Si el Gateway usará Spring Cloud, agregarlo al `settings.gradle.kts`.

### B. Crear el Módulo de Pedidos (`orders-service`)
Este es el siguiente gran bloque lógico.

**Reglas para su creación:**
1. **IDs:** Debe usar UUIDs para `Order`, `OrderItem`, etc.
2. **Dependencias:** Debe incluir `project(":shared-observability")` en su `build.gradle.kts`.
3. **Seguridad:** Debe replicar la configuración de seguridad del `catalog-service` (`SecurityConfig.java` + Clave Pública) para validar que el usuario que hace el pedido está autenticado.
4. **Base de Datos:**
   - Debe usar adaptadores JPA apuntando a la misma base de datos local.
   - **Importante:** NO debe activar Flyway (`spring.flyway.enabled=false`). Las tablas de pedidos (ej. `orders`, `order_items`) ya se crearon en la migración `V4__catalog_and_orders.sql` del `auth-service`.
5. **Comunicación Sincrónica:** Al crear un pedido, el `orders-service` debe validar que los productos existen. Para esto, debe hacer una petición HTTP (usando RestClient o Feign) al endpoint `POST /catalog/products/validate` del `catalog-service`.

### C. (Sugerencia de Arquitectura IA) RPA con n8n
Una vez el `orders-service` esté listo, se recomienda delegar toda la lógica asíncrona pesada (notificaciones a restaurantes por WhatsApp, correos de confirmación, análisis de fraude) a una herramienta como **n8n**. 
El `orders-service` solo tendría que enviar un Webhook simple a n8n, y el flujo de RPA (potenciado por un LLM como Gemini para entender notas del cliente) se encargaría del resto de la orquestación.

---
*Fin del Handover.*

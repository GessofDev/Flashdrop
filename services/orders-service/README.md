# FlashDrop - Orders Service

Microservicio de gestión de pedidos para FlashDrop, implementado con **Arquitectura Hexagonal (Ports & Adapters)** y **Spring Boot 3.2**.

Responsable del ciclo de vida completo de los pedidos: creación, seguimiento, asignación de repartidores y actualización de estados.

## Arquitectura

Orders Service es **propietario exclusivo** de las tablas `client`, `orders` y `order_items` en su
**propia base de datos Supabase**. Toda la información de otros dominios (`users`, `products`,
`restaurant`, `delivery`, `delivery_routes`) se obtiene mediante **contratos HTTP internos** con
Auth, Catalog y Delivery. **Orders nunca accede directamente a tablas ajenas.**

```text
                    HTTP CONTRACTS
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
      AUTH           CATALOG          DELIVERY
        │                │                │
      AUTH DB         CATALOG DB       DELIVERY DB


                    ORDERS SERVICE
                         │
                         ▼
              ORDERS DB  (Supabase / PostgREST /rest/v1, schema public)
                         │
              ┌──────────┼──────────┐
              │          │          │
            client      orders   order_items
```

```text
┌────────────────────────────── CONTROLLERS ─────────────────────────────┐
│  OrderController · InternalOrdersController · HealthController         │
└───────────────────────┬────────────────────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────────────────┐
│                              USE CASES                                │
│  CreateOrder · ListOrders · GetOrderDetail · UpdateStatus · ClaimDelivery │
└──────────────┬─────────────────────────────────────┬───────────────────┘
               │                                     │
┌──────────────▼────────┐          ┌────────────────▼──────────┐
│      DOMAIN          │          │      PORTS (interfaces)    │
│  Order · OrderItem    │          │  OrderRepositoryPort        │
│  OrderStatus · ...    │          │  ClientPort                 │
└──────────────────────┘          │  CatalogPort                │
                                  │  DeliveryPort               │
                                  │  UserPort                   │
                                  │  EventPublisherPort         │
                                  └──────────────┬──────────────┘
                                                 │
          ┌──────────────────────────────────────┼──────────────────────────┐
          │                                      │                          │
┌─────────▼──────────────┐         ┌─────────────▼─────────────┐   ┌──────────▼────────┐
│  SUPABASE ADAPTERS     │         │  HTTP OUTBOUND ADAPTERS   │   │ MESSAGE ADAPTER   │
│  (REST a PostgREST)    │         │  (a otros servicios)      │   │                   │
│  propias de Orders:    │         │                           │   │  RabbitMQEvent    │
│  SupabaseRestOrder-   │         │  CatalogHttpClientAdapter  │   │  Publisher        │
│  RepositoryAdapter     │         │   (Catalog C-1..C-3)      │   │  (EventPublisher  │
│   → orders, order_items│         │  AuthHttpClientAdapter     │   │   Port)           │
│  SupabaseRestClient    │         │   (Auth C-4)              │   │                   │
│  Adapter (→ client)    │         │  DeliveryHttpClientAdapter │   │                   │
└─────────┬──────────────┘         │   (Delivery C-5..C-7)       │   └───────────────────┘
          │                        └───────────────────────────┘             │
          └────────────────────────────────────┬──────────────────────────────┘
                                               │
                          ┌────────────────────▼────────────────────┐
                          │  Supabase Kong / PostgREST (/rest/v1)    │
                          │  + Internal service URLs (Auth/Catalog/   │
                          │    Delivery)                              │
                          └──────────────────────────────────────────┘
```

## Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Persistencia | Supabase REST API (vía Kong/PostgREST) — **BD propia de Orders** |
| Mensajería | RabbitMQ (AMQP) |
| Build | Maven |
| Testing | JUnit 5 + Mockito |

> **Nota:** Orders persiste exclusivamente vía **REST API** (PostgREST). No usa JDBC/JPA ni Flyway.
> La infraestructura Supabase de Orders es **provisional y configurable** mediante variables de
> entorno; puede reemplazarse sin tocar Domain, Application, Ports ni Controllers.

## Estructura de Carpetas

```text
src/main/java/cl/flashdrop/orders/
├── OrdersServiceApplication.java
├── config/
│   ├── CorsConfig.java
│   ├── RabbitMQConfig.java
│   ├── SupabaseRestClientConfig.java      ← bean RestClient (único punto de acoplamiento a Supabase)
│   ├── InternalServiceClientConfig.java   ← RestClients hacia Auth/Catalog/Delivery
│   ├── SecurityConfig.java
│   ├── JwtValidationFilter.java
│   └── InternalApiKeyFilter.java
├── domain/
│   ├── model/          → Order, OrderItem, OrderStatus, ClientInfo, ...
│   ├── port/           → OrderRepositoryPort, ClientPort, CatalogPort, DeliveryPort, UserPort, EventPublisherPort
│   └── exception/      → OrderDomainException
├── application/
│   ├── command/        → CreateOrderCommand
│   ├── dto/            → CreatedOrderResult
│   ├── usecase/        → CreateOrderUseCase, ListOrdersUseCase, GetOrderDetailUseCase, UpdateOrderStatusUseCase, ClaimDeliveryOrdersUseCase
│   └── OrderEnricher.java
└── infrastructure/
    ├── api/            → Controllers + DTOs request/response (Health, OrderController, InternalOrdersController)
    ├── exception/      → ExternalServiceException, GlobalExceptionHandler
    ├── messaging/      → RabbitMQEventPublisher + Event DTOs
    ├── adapter/outbound/http/      → CatalogHttpClientAdapter, AuthHttpClientAdapter, DeliveryHttpClientAdapter, InternalHttpSupport
    └── adapter/outbound/persistence/supabase/  → SupabaseRestOrderRepositoryAdapter, SupabaseRestClientAdapter
        dto/          → OrderRow, OrderItemRow, ClientRow  (representan tablas PROPIAS de Orders)
```

## Tablas propias de Orders (acceso directo vía PostgREST)

| Tabla | Puerto/Adapter | Propósito |
|---|---|---|
| `orders` | `OrderRepositoryPort` → `SupabaseRestOrderRepositoryAdapter` | Ciclo de vida del pedido |
| `order_items` | `OrderRepositoryPort` → `SupabaseRestOrderRepositoryAdapter` | Ítems de cada pedido |
| `client` | `ClientPort` → `SupabaseRestClientAdapter` | Perfil cliente (enriquecido con Auth C-4) |

> `product_id`, `restaurant_id`, `delivery_id`, `user_id` son referencias **externas** (sin FK local).
> La información externa se obtiene por HTTP: `products`/`restaurant` (Catalog C-1..C-3), `users` (Auth C-4), `delivery`/`delivery_routes` (Delivery C-5..C-7).

## Variables de Entorno

| Variable | Descripción | Obligatoria |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo (`supabase`) | Sí |
| `SUPABASE_URL` | URL base de Supabase Kong/PostgREST — **BD EXCLUSIVA de Orders** | Sí |
| `SUPABASE_SERVICE_ROLE_KEY` | Service Role Key de Supabase — **BD EXCLUSIVA de Orders** | Sí |
| `AUTH_SERVICE_URL` | URL de Auth Service (validación JWT + usuarios) | No (default localhost:8081) |
| `CATALOG_SERVICE_URL` | URL de Catalog Service (productos/restaurantes) | No (default localhost:8082) |
| `DELIVERY_SERVICE_URL` | URL de Delivery Service (repartidores/rutas) | No (default localhost:8084) |
| `INTERNAL_API_KEY` | Clave para endpoints `/api/internal/*` | No (default dev-key) |
| `DELIVERY_FEE` | Tarifa de delivery en CLP (default: 2500) | No |
| `RABBITMQ_HOST` | Host RabbitMQ (default: localhost) | No |
| `RABBITMQ_PORT` | Puerto RabbitMQ (default: 5672) | No |
| `RABBITMQ_USERNAME` | Usuario RabbitMQ (default: guest) | No |
| `RABBITMQ_PASSWORD` | Contraseña RabbitMQ (default: guest) | No |

En el stack Docker (`gateway/docker/docker-compose.stack.yml`) Orders recibe su propia
proyecto Supabase mediante las variables de host `ORDERS_SUPABASE_URL` /
`ORDERS_SUPABASE_SERVICE_ROLE_KEY`, que se inyectan dentro del contenedor como
`SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY`. Así Orders queda **aislado** de Auth/Catalog/Delivery.

## Cómo Levantar

### Prerrequisitos

- Java 21+
- Maven 3.8+
- RabbitMQ (opcional, el servicio arranca sin él)
- Un proyecto Supabase con las tablas `client`, `orders`, `order_items` (provisioning manual,
  ejecutar `src/main/resources/db/migration/V1__init.sql` y `db/migration_local/V1_1__seed_local_data.sql`).

### Pasos

```bash
# 1. Descargar el repositorio
git clone <repo-url>
cd services/orders-service

# 2. Crear .env con las credenciales EXCLUSIVAS de Orders (ver .env.example)
cp .env.example .env
# Editar .env: SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY de tu proyecto Supabase de Orders

# 3. Compilar
mvn clean compile

# 4. Ejecutar con el perfil supabase
mvn spring-boot:run -Dspring-boot.run.profiles=supabase
```

### Health Check

```bash
curl http://localhost:8083/health
```

Respuesta esperada:
```json
{ "service": "orders-service", "status": "ok" }
```

## Endpoints

### Orders

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/orders` | Listar pedidos (opcional: `?user_id=UUID`) |
| `GET` | `/api/orders/{id}` | Obtener detalle de pedido |
| `POST` | `/api/orders` | Crear nuevo pedido |
| `PUT` | `/api/orders/{id}/status` | Actualizar estado del pedido |

### Delivery

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/delivery/routes` | Listar rutas de entrega |
| `POST` | `/api/delivery/claim` | Repartidor reclama pedidos |

## Perfil Supabase

El perfil `supabase` excluye las auto-configuraciones JDBC/JPA/Flyway, ya que el servicio
persiste exclusivamente vía REST API (PostgREST):

```properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
  org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,\
  org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
```

## Conexión a Supabase (BD propia de Orders)

El servicio se conecta a Supabase **exclusivamente mediante REST API**, no por JDBC directo.

Cada request al PostgREST incluye los headers:
- `apikey: SUPABASE_SERVICE_ROLE_KEY`
- `Authorization: Bearer SUPABASE_SERVICE_ROLE_KEY`
- `Accept: application/json`

Tablas propias de Orders consultadas directamente (schema `public`):
- `client`, `orders`, `order_items`

La información de otros dominios se obtiene mediante contratos HTTP internos:
- `products` / `restaurant` → Catalog Service (C-1, C-2, C-3)
- `users` → Auth Service (C-4)
- `delivery` / `delivery_routes` → Delivery Service (C-5, C-6, C-7)

## Tests

```bash
mvn test
```

Los tests usan JUnit 5 + Mockito. Mockean los puertos del dominio (interfaces), por lo que
no requieren base de datos ni RabbitMQ.

## Licencia

Privado — FlashDrop App

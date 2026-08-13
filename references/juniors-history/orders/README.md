# FlashDrop - Orders Service

Microservicio de gestión de pedidos para FlashDrop, implementado con **Arquitectura Hexagonal (Ports & Adapters)** y **Spring Boot 3.2**.

Responsable del ciclo de vida completo de los pedidos: creación, seguimiento, asignación de repartidores y actualización de estados.

## Arquitectura

```
┌─────────────────────────────────────────────────────┐
│                    CONTROLLERS                       │
│  OrderController · DeliveryRouteController · Health │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   USE CASES                          │
│  CreateOrder · ListOrders · GetOrderDetail           │
│  UpdateStatus · ClaimDelivery · ListRoutes           │
└──────┬─────────────────────────────────┬────────────┘
       │                                 │
┌──────▼──────┐                 ┌───────▼──────────────┐
│   DOMAIN    │                 │     PORTS (interfaces)│
│  Order ·    │                 │ OrderRepositoryPort   │
│  OrderItem  │                 │ CatalogPort           │
│  OrderStatus│                 │ DeliveryPort          │
│  DeliveryRouter│              │ EventPublisherPort    │
└─────────────┘                 └───────┬──────────────┘
                                        │
               ┌────────────────────────┼────────────────────┐
               │                        │                    │
      ┌────────▼────────┐    ┌──────────▼──────┐  ┌─────────▼──────┐
      │ SupabaseRestOrder│   │SupabaseRestCatalog│ │SupabaseRestDeliv│
      │ RepositoryAdapter│   │    Adapter       │ │   eryAdapter    │
      └────────┬────────┘    └────────┬─────────┘ └────────┬───────┘
               │                      │                    │
               └──────────────────────┼────────────────────┘
                                      │
                         ┌────────────▼────────────┐
                         │   Supabase Kong / PostgREST │
                         │       REST API /rest/v1      │
                         └─────────────────────────────┘
```

## Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Persistencia | Supabase REST API (vía Kong/PostgREST) |
| Mensajería | RabbitMQ (AMQP) |
| Build | Maven |
| Testing | JUnit 5 + Mockito |

## Estructura de Carpetas

```
src/main/java/cl/flashdrop/orders/
├── OrdersServiceApplication.java
├── config/
│   ├── CorsConfig.java
│   ├── RabbitMQConfig.java
│   └── SupabaseRestClientConfig.java
├── domain/
│   ├── model/          → Order, OrderItem, OrderStatus, DeliveryRoute, etc.
│   ├── port/           → OrderRepositoryPort, CatalogPort, DeliveryPort, EventPublisherPort
│   └── exception/      → OrderDomainException
├── application/
│   ├── command/        → CreateOrderCommand
│   ├── dto/            → CreatedOrderResult
│   └── usecase/        → CreateOrderUseCase, ListOrdersUseCase, etc.
└── infrastructure/
    ├── api/            → Controllers + DTOs request/response
    ├── exception/      → GlobalExceptionHandler
    ├── messaging/      → RabbitMQEventPublisher + Event DTOs
    └── persistence/
        ├── adapter/    → SupabaseRestOrderRepositoryAdapter
        │                 SupabaseRestCatalogAdapter
        │                 SupabaseRestDeliveryAdapter
        └── dto/        → OrderRow, OrderItemRow, DeliveryRouteRow, etc.
```

## Variables de Entorno

| Variable | Descripción | Obligatoria |
|---|---|---|
| `SUPABASE_URL` | URL base de Supabase Kong/PostgREST | Sí |
| `SUPABASE_SERVICE_ROLE_KEY` | Service Role Key de Supabase | Sí |
| `SPRING_PROFILES_ACTIVE` | Perfil activo (`supabase`) | Sí |
| `DELIVERY_FEE` | Tarifa de delivery en CLP (default: 2500) | No |
| `RABBITMQ_HOST` | Host RabbitMQ (default: localhost) | No |
| `RABBITMQ_PORT` | Puerto RabbitMQ (default: 5672) | No |
| `RABBITMQ_USERNAME` | Usuario RabbitMQ (default: guest) | No |
| `RABBITMQ_PASSWORD` | Contraseña RabbitMQ (default: guest) | No |

## Cómo Levantar

### Prerrequisitos

- Java 21+
- Maven 3.8+
- RabbitMQ (opcional, el servicio arranca sin él)

### Pasos

```bash
# 1. Clonar el repositorio
git clone <repo-url>
cd orders-service

# 2. Crear archivo .env con las variables reales (ver .env.example)
cp .env.example .env
# Editar .env con SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY reales

# 3. Compilar
mvn clean compile

# 4. Ejecutar con perfil supabase
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

El perfil `supabase` excluye las auto-configuraciones de JDBC/JPA/Flyway:

```properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
  org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,\
  org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
```

## Tests

```bash
mvn test
```

Los tests usan JUnit 5 + Mockito. Mockean los puertos del dominio (interfaces), no requieren base de datos ni RabbitMQ.

## Conexión a Supabase

El servicio se conecta a Supabase exclusivamente mediante **REST API**, no por JDBC directo.

Cada request incluye los headers:
- `apikey: SUPABASE_SERVICE_ROLE_KEY`
- `Authorization: Bearer SUPABASE_SERVICE_ROLE_KEY`
- `Accept: application/json`

Tablas consultadas:
- `orders`, `order_items`, `delivery_routes`, `delivery_claims`
- `products`, `restaurant`, `client`, `users`, `delivery`

## Licencia

Privado — FlashDrop App
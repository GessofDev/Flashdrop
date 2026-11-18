# Proposal: `ratings-system`

## 1. Problem statement

FlashDrop no tiene forma de calificar a los actores de una orden. Un cliente
que recibió una mala entrega o un pedido incorrecto no tiene dónde dejar su
reclamación; una tienda que pierde pedidos por tiempos de retiro tampoco; un
repartidor no tiene señal de calidad sobre su trabajo.

Hoy el ciclo de vida de la orden termina en `ENTREGADO` y no hay ningún
artefacto de opinión: ni en `orders-service`, ni en `catalog-service`, ni en
`delivery-service`, ni en la app web (verificado por búsqueda en todo el repo:
no existe ninguna tabla, endpoint, DTO ni componente de ratings).

Este change agrega un sistema de calificaciones **anclado a la orden** que
permite calificar a clientes, repartidores y tiendas, expone promedios públicos
y deja el dato en un servicio nuevo con autoridad propia sobre el dominio de
"calificación".

## 2. Target users / situations

**Usuarios internos:** la app web (React) y las apps Flutter (cliente,
repartidor, tienda) consumiendo vía Gateway.

**Casos cubiertos:**

- El cliente recibe su pedido (`ENTREGADO`) y califica al repartidor y a la
  tienda, con estrellas y un comentario opcional.
- El repartidor, tras entregar, califica a la tienda (tiempos de preparación,
  exactitud del pedido) y al cliente (cómo recibió el pedido).
- La tienda, tras la entrega, califica al repartidor (puntualidad) y al
  cliente (disponibilidad para recibir).
- Cualquier usuario autenticado ve el promedio público de una tienda en el
  marketplace y del repartidor en su perfil.

**Fuera de alcance de este change:** moderación y panel de administración de
reseñas (queda como tarea posterior, T17 del plan), edición de una reseña ya
escrita, respuestas del actor calificado, imágenes en las reseñas y analítica
avanzada.

## 3. Decisiones tomadas con el equipo

| Decisión | Elección | Motivo |
|---|---|---|
| D1 · Dónde vive el dato | **Servicio nuevo `reviews-service`** con base propia | El dominio de "calificación" tiene reglas propias (quién puede calificar a quién, ventana de la orden, idempotencia, agregados públicos) y crece hacia moderación y analítica. Aislarlo evita que `orders-service` cargue con un concern que no es del ciclo de vida de la orden, y deja el agregado público disponible sin filtrar datos de órdenes. |
| D2 · Quién califica a quién | **Matriz completa de 6 relaciones** | Simetría entre los tres actores: cliente → repartidor/tienda, repartidor → tienda/cliente, tienda → repartidor/cliente. El pedido original menciona a los tres como actores calificables. |
| D3 · Estrellas + texto | **1-5 estrellas obligatorias + comentario opcional (≤ 500 caracteres)** | El promedio solo con estrellas no distingue "malo servicio" de "mala comida". El texto entra sanitizado y con longitud acotada; la moderación queda fuera de v1. |
| D4 · Visibilidad | **Promedio y comentarios públicos** | El marketplace necesita señales comparables y el texto es información pública sobre la operación. Se renderiza como texto plano, nunca HTML. |

## 4. Impacto

**Servicios nuevos**

- `services/reviews-service`: Spring Boot 3 (Gradle Kotlin DSL), puerto 8085,
  base `reviews_db`, migraciones Flyway propias, tabla `reviews`.
- Endpoint interno nuevo en `orders-service`:
  `GET /api/internal/orders/{id}` — resumen de la orden con sus participantes
  en ids numéricos públicos, sin datos de contacto.

**Servicios modificados**

- `catalog-service`: tabla `restaurant_rating_stats`, pull interno por lotes,
  `ratingAverage` / `ratingCount` en la ficha del restaurante.
- `delivery-service`: tabla `delivery_person_rating_stats`, pull interno por
  lotes, promedio en el perfil del repartidor.
- `gateway`: ruta `/api/reviews` → `http://reviews-service:8085` + rate limit
  de escritura.
- `apps/web`: formulario de calificación post-entrega y visualización de
  promedios.

**Infraestructura**

- `.github/workflows/reviews-service-ci.yml` y `reviews-service-cd.yml`.
- `infra/floci/task-definitions/reviews-service.dev.json`.
- Grants de `reviews_app` sobre `reviews_db` documentados en
  `infra/floci/INFRASTRUCTURE.md` §4.
- Entrada del servicio en `gateway/docker/docker-compose.stack.yml`.

## 5. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | Un usuario califica a un actor con el que no tiene relación | El `targetId` lo deriva el servidor desde la orden; el cliente solo declara el **tipo** de destino. La matriz de autorización se valida contra la participación real en la orden. |
| 2 | Abuso de la API de escritura | Rate limit por path en el gateway, unicidad por (orden, autor, destino), roles verificados contra el claim `roles` del JWT. |
| 3 | Contratos públicos nuevos que rompen consumidores | Los endpoints nuevos son aditivos; `GET /api/orders/**` no cambia de comportamiento y hay un chequeo diferencial explícito en el plan. |
| 4 | Acoplamiento de `reviews-service` con `orders-service` | Se habla por HTTP interno (`X-Internal-Api-Key`), nunca por base de datos. Si orders no responde, la escritura de la calificación falla con 5xx y no se guarda nada. |
| 5 | El marketplace no puede depender de la disponibilidad de `reviews-service` | `catalog-service` y `delivery-service` cachean el último agregado conocido y sirven ese valor si el pull falla. |

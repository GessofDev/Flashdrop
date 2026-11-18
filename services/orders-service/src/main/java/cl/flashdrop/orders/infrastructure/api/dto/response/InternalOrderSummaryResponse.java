package cl.flashdrop.orders.infrastructure.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resumen de una orden para un servicio interno (contrato T4 del feature
 * {@code ratings-system}).
 *
 * <p>Es lo que consume {@code reviews-service} para validar la participación del
 * autor en la orden y derivar el destino de la calificación (S7).</p>
 *
 * <p><b>No expone datos de contacto</b>: ni dirección, ni teléfono, ni nombre
 * del repartidor, ni items, ni importes.</p>
 *
 * <p><b>Espacio de ids.</b> Todos los ids son numéricos públicos:</p>
 * <ul>
 *   <li>{@code orderId} → id público de la orden.</li>
 *   <li>{@code clientId} → {@code auth.users.id} del cliente de la orden. <b>No</b> es
 *       el id del perfil de cliente ({@code public.client.id}): es el id de usuario de
 *       Auth, que es el que el cliente reconoce y el único comparable contra el
 *       {@code sub} del JWT. Por eso se resuelve desde {@code client.user_id}.</li>
 *   <li>{@code restaurantId} → {@code catalog.restaurant.id}.</li>
 *   <li>{@code courierUserId} → {@code auth.users.id} del repartidor, o {@code null}
 *       si la orden no tiene repartidor.</li>
 * </ul>
 *
 * @param orderId       id público de la orden.
 * @param status        estado de la orden (p. ej. {@code ENTREGADO}).
 * @param clientId      id de usuario (auth) del cliente de la orden.
 * @param restaurantId  id de restaurante (catalog) de la orden.
 * @param courierUserId id de usuario (auth) del repartidor, o {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InternalOrderSummaryResponse(
        Long orderId,
        String status,
        Long clientId,
        Long restaurantId,
        Long courierUserId
) {
}

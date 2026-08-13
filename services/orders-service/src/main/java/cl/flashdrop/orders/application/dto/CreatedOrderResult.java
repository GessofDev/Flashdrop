package cl.flashdrop.orders.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado de la creación de un pedido.
 */
public record CreatedOrderResult(UUID id, BigDecimal total) {}

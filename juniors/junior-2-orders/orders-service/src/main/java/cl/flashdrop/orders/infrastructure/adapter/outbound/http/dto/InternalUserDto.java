package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import java.util.UUID;

/**
 * DTO de transporte interno de Auth para usuarios.
 *
 * <p>Orders no expone ni almacena usuarios: consume este endpoint interno
 * en lugar de consultar la tabla {@code users} de Supabase.</p>
 */
public record InternalUserDto(
        Long id,
        String fullName,
        String email,
        String phone
) {
}

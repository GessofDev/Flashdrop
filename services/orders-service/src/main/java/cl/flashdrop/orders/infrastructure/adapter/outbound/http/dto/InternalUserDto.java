package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import java.util.UUID;

/**
 * DTO de transporte interno de Auth para usuarios.
 *
 * <p>Orders no expone ni almacena usuarios: consume este endpoint interno
 * en lugar de consultar la tabla {@code users} de Supabase.</p>
 *
 * <p>Refleja el contrato real de {@code GET /api/internal/users/{id}} definido en
 * {@code MIGRATION_PLAN.md} (sección 8.1): Auth expone {@code name} y {@code lastName}
 * por separado, nunca {@code fullName}.</p>
 */
public record InternalUserDto(
        Long id,
        String name,
        String lastName,
        String email,
        String phone
) {
}

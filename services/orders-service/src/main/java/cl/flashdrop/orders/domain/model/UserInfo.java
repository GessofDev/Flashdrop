package cl.flashdrop.orders.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * Información básica de un usuario obtenida de Auth (C-4).
 */
@Value
@AllArgsConstructor
@Builder
public class UserInfo {
    String fullName;
    String email;
    String phone;
}

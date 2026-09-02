package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.InternalRoleResponse;
import com.flashdrop.auth.application.dto.InternalUserResponse;

import java.util.List;

public interface GetInternalUserUseCase {
    InternalUserResponse getUser(Long userId);

    /**
     * Variante batch de {@link #getUser(Long)}. Los IDs inexistentes se
     * ignoran en vez de fallar: el consumidor (Orders listando pedidos)
     * necesita resolver muchos usuarios de una vez y un id colgado —posible
     * ahora que no hay FK entre bases— no debe tumbar la respuesta completa.
     *
     * @return siempre una lista; vacía si ningún id existe.
     */
    List<InternalUserResponse> getUsers(List<Long> userIds);

    List<InternalRoleResponse> getRoles(Long userId);
}

package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.flashdrop.auth.application.dto.InternalRoleResponse;
import com.flashdrop.auth.application.dto.InternalUserResponse;
import com.flashdrop.auth.application.port.inbound.GetInternalUserUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/users")
public class InternalUserController {

    private final GetInternalUserUseCase getInternalUser;

    public InternalUserController(GetInternalUserUseCase getInternalUser) {
        this.getInternalUser = getInternalUser;
    }

    @GetMapping("/{userId}")
    public InternalUserResponse getUser(@PathVariable Long userId) {
        return getInternalUser.getUser(userId);
    }

    /**
     * Variante batch: GET /api/internal/users?ids=1,2,3
     *
     * <p>Existe para que Orders pueda resolver los datos de cliente de una
     * lista de pedidos con una sola llamada en vez de una por pedido. Devuelve
     * siempre un array e ignora los ids que no existen, igual que
     * {@code GET /api/internal/products?ids=} de Catalog.
     */
    @GetMapping
    public List<InternalUserResponse> getUsers(
            @RequestParam(name = "ids", required = false) List<Long> ids) {
        return getInternalUser.getUsers(ids == null ? List.of() : ids);
    }

    @GetMapping("/{userId}/roles")
    public List<InternalRoleResponse> getRoles(@PathVariable Long userId) {
        return getInternalUser.getRoles(userId);
    }
}

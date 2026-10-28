package com.flashdrop.auth.infrastructure.adapter.inbound.rest;

import com.flashdrop.auth.application.dto.AssignUserRolesCommand;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;
import com.flashdrop.auth.application.dto.InternalRoleResponse;
import com.flashdrop.auth.application.dto.InternalUserResponse;
import com.flashdrop.auth.application.port.inbound.AssignUserRolesUseCase;
import com.flashdrop.auth.application.port.inbound.GetInternalUserUseCase;
import com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto.AssignRolesRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/users")
public class InternalUserController {

    private final GetInternalUserUseCase getInternalUser;
    private final AssignUserRolesUseCase assignUserRoles;

    public InternalUserController(GetInternalUserUseCase getInternalUser,
                                  AssignUserRolesUseCase assignUserRoles) {
        this.getInternalUser = getInternalUser;
        this.assignUserRoles = assignUserRoles;
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

    /**
     * Reemplaza el conjunto de roles del usuario.
     *
     * <p>Es la contraparte de escritura del GET de arriba y el unico modo de
     * asignar un rol que no sea el base "Cliente" del registro. La protege la
     * misma clave interna que el resto de {@code /api/internal/**}; el
     * panel de administracion consumira este mismo endpoint cuando exista.
     *
     * <p>El cuerpo define el conjunto final: los roles que no aparecen dejan
     * de estar asignados y una lista vacia deja al usuario sin roles.
     */
    @PutMapping("/{userId}/roles")
    public AssignUserRolesResult assignRoles(@PathVariable Long userId,
                                              @RequestBody AssignRolesRequest request,
                                              HttpServletRequest http) {
        return assignUserRoles.assign(
                new AssignUserRolesCommand(userId, request.roles(), clientIp(http)));
    }

    /**
     * Origen de la peticion para la auditoria. Detras de un proxy el address
     * remoto es el del proxy, asi que primero se mira X-Forwarded-For.
     */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}

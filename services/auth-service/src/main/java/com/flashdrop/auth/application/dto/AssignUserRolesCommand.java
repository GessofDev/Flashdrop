package com.flashdrop.auth.application.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Asignacion de roles a un usuario.
 *
 * @param userId   usuario destinatario
 * @param roleNames nombres de los roles; el conjunto final reemplaza al actual
 * @param clientIp origen de la peticion, para la auditoria
 */
public record AssignUserRolesCommand(Long userId, List<String> roleNames, String clientIp) {

    /**
     * Copia defensiva: el record es inmutable y no puede quedar mutado por
     * el que lo construyo. No usa {@code List.copyOf} porque ese rechaza los
     * elementos null y el caso de uso distingue "nombre de rol vacio" de
     * "lista de roles vacia".
     */
    public AssignUserRolesCommand {
        roleNames = roleNames == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(roleNames));
    }
}

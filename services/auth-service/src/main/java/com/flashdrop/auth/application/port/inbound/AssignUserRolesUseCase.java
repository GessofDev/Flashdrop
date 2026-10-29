package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.AssignUserRolesCommand;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;

/**
 * Asigna (reemplaza) el conjunto de roles de un usuario.
 *
 * <p>Es la operacion que consumira el panel de administracion cuando exista;
 * mientras tanto la usan los scripts de QA.
 */
public interface AssignUserRolesUseCase {

    AssignUserRolesResult assign(AssignUserRolesCommand command);
}

package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.dto.AssignUserRolesCommand;
import com.flashdrop.auth.application.dto.AssignUserRolesResult;
import com.flashdrop.auth.application.dto.InternalRoleResponse;
import com.flashdrop.auth.application.port.inbound.AssignUserRolesUseCase;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.RoleRepository;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.InvalidRoleException;
import com.flashdrop.auth.domain.exception.InvalidUserException;
import com.flashdrop.auth.domain.model.Role;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reemplaza el conjunto de roles de un usuario.
 *
 * <p>Es la contraparte de escritura del GET de roles. El contrato es de
 * reemplazo, no de delta: el cuerpo define el conjunto final y lo que no
 * aparece deja de estar asignado. Asi el panel de administracion y los
 * scripts de QA pueden usar la misma operacion sin depender uno del otro.
 *
 * <p>Los nombres de rol se resuelven contra la tabla {@code roles}: el
 * nombre canonico que se persiste es el de la base, no el que escribio el
 * llamador. La comparacion no distingue mayusculas para que "repartidor" y
 * "Repartidor" sean el mismo rol, pero el resultado siempre devuelve el
 * nombre canonico.
 *
 * <p><b>Sobre quien puede llamar.</b> Hoy la unica puerta es la clave
 * interna compartida por los cinco despliegues, asi que no hay una
 * identidad de llamador: la auditoria registra el principal
 * {@value #ACTOR}, el origen de la peticion y el usuario afectado. Cuando
 * exista el panel de administracion hay que sumar una identidad real (un rol
 * admin o una credencial por servicio) antes de exponer esto mas alla de
 * QA.
 */
public class AssignUserRolesService implements AssignUserRolesUseCase {

    private static final String ACTION = "ASSIGN_ROLES";

    /** Principal que ejecuta el cambio mientras no haya identidad de llamador. */
    private static final String ACTOR = "internal-api";

    private final UserRepository users;
    private final RoleRepository roles;
    private final AuditLogger audit;

    public AssignUserRolesService(UserRepository users, RoleRepository roles, AuditLogger audit) {
        this.users = users;
        this.roles = roles;
        this.audit = audit;
    }

    /**
     * La asignacion de roles es un cambio de permisos: se audita en los dos
     * desenlaces, igual que el alta de usuarios.
     */
    @Override
    public AssignUserRolesResult assign(AssignUserRolesCommand command) {
        Long userId = command.userId();
        String target = userId == null ? null : String.valueOf(userId);
        try {
            return asignar(command);
        } catch (RuntimeException e) {
            audit.record(new AuditLogger.AuditEvent(ACTION, ACTOR, "FAIL", command.clientIp(), target));
            throw e;
        }
    }

    private AssignUserRolesResult asignar(AssignUserRolesCommand command) {
        Long userId = command.userId();
        if (userId == null) {
            throw new InvalidUserException("El usuario es obligatorio para asignar roles");
        }

        List<String> solicitados = normalizar(command.roleNames());

        Map<String, Role> porNombre = indexPorNombre(roles.findAll());
        List<String> desconocidos = solicitados.stream()
                .filter(nombre -> !porNombre.containsKey(clave(nombre)))
                .toList();
        if (!desconocidos.isEmpty()) {
            throw new InvalidRoleException("Rol desconocido: " + String.join(", ", desconocidos));
        }

        List<Role> resueltos = solicitados.stream()
                .map(nombre -> porNombre.get(clave(nombre)))
                .toList();

        // La escritura verifica que el usuario exista dentro de su propia
        // transaccion: comprobarlo aca abriria una ventana en la que un
        // borrado concurrente dejaria el INSERT pegando contra la FK.
        users.assignRoles(userId, resueltos);

        audit.record(new AuditLogger.AuditEvent(
                ACTION, ACTOR, "SUCCESS", command.clientIp(), String.valueOf(userId)));
        return new AssignUserRolesResult(userId, resueltos.stream()
                .map(rol -> new InternalRoleResponse(rol.id(), rol.name()))
                .toList());
    }

    /**
     * Limpia los nombres pedidos: recorta espacios, rechaza vacios y
     * elimina repeticiones. El orden pedido se respeta.
     */
    private List<String> normalizar(List<String> nombres) {
        if (nombres == null) {
            throw new InvalidRoleException("La lista de roles es obligatoria");
        }
        Map<String, String> unicos = new LinkedHashMap<>();
        for (String nombre : nombres) {
            if (nombre == null || nombre.isBlank()) {
                throw new InvalidRoleException("El nombre del rol no puede estar vacio");
            }
            String limpio = nombre.trim();
            unicos.putIfAbsent(clave(limpio), limpio);
        }
        return List.copyOf(unicos.values());
    }

    private Map<String, Role> indexPorNombre(List<Role> disponibles) {
        Map<String, Role> indice = new LinkedHashMap<>();
        for (Role rol : disponibles) {
            indice.putIfAbsent(clave(rol.name()), rol);
        }
        return indice;
    }

    private static String clave(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }
}

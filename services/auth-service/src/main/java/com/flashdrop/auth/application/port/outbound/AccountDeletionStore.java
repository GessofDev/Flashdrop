package com.flashdrop.auth.application.port.outbound;

import java.time.Instant;
import java.util.List;

import com.flashdrop.auth.domain.model.AccountDeletion;

/** Cola persistente de cuentas en eliminacion. */
public interface AccountDeletionStore {

    /** Registra la solicitud. Devuelve false si esa cuenta ya tenia una en curso. */
    boolean create(Long userId, Instant now);

    /** Solicitudes pendientes cuyo proximo intento ya vencio, las mas antiguas primero. */
    List<AccountDeletion> findDue(Instant now, int limit);

    void save(AccountDeletion deletion);

    void delete(Long userId);
}

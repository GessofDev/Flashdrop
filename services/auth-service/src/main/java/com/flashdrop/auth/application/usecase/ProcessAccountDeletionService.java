package com.flashdrop.auth.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flashdrop.auth.application.port.inbound.ProcessAccountDeletionUseCase;
import com.flashdrop.auth.application.port.outbound.AccountDeletionStore;
import com.flashdrop.auth.application.port.outbound.AccountErasurePort;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.exception.AccountDeletionBlockedException;
import com.flashdrop.auth.domain.model.AccountDeletion;

/**
 * Completa en segundo plano la eliminacion de cuentas: Orders, luego Delivery, luego Catalog y al final
 * borra al usuario en Auth. El orden importa: Orders necesita resolver al cliente, tienda o repartidor
 * mientras Catalog y Delivery todavia los conocen. Cada paso es idempotente y su avance se guarda, asi un
 * reintento continua donde quedo. Si aparecen pedidos en curso (carrera entre la solicitud y el borrado) la
 * eliminacion se aborta y la cuenta se reactiva: nunca se cancela nada.
 */
public class ProcessAccountDeletionService implements ProcessAccountDeletionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessAccountDeletionService.class);
    private static final String ACTION = "ACCOUNT_DELETION";
    private static final int BATCH = 20;
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final AccountDeletionStore deletions;
    private final AccountErasurePort erasure;
    private final UserRepository users;
    private final CredentialStore credentials;
    private final AuditLogger audit;
    private final Clock clock;
    private final int maxAttempts;

    public ProcessAccountDeletionService(AccountDeletionStore deletions, AccountErasurePort erasure,
                                         UserRepository users, CredentialStore credentials,
                                         AuditLogger audit, Clock clock, int maxAttempts) {
        this.deletions = deletions;
        this.erasure = erasure;
        this.users = users;
        this.credentials = credentials;
        this.audit = audit;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public int processDue() {
        List<AccountDeletion> due = deletions.findDue(clock.instant(), BATCH);
        int completed = 0;
        for (AccountDeletion deletion : due) {
            if (process(deletion)) {
                completed++;
            }
        }
        return completed;
    }

    private boolean process(AccountDeletion deletion) {
        Long userId = deletion.userId();
        String target = String.valueOf(userId);
        AccountDeletion current = deletion;
        try {
            if (!current.ordersDone()) {
                erasure.eraseOrders(userId);
                current = current.withOrdersDone();
                deletions.save(current);
            }
            if (!current.deliveryDone()) {
                erasure.eraseDelivery(userId);
                current = current.withDeliveryDone();
                deletions.save(current);
            }
            if (!current.catalogDone()) {
                erasure.eraseCatalog(userId);
                current = current.withCatalogDone();
                deletions.save(current);
            }
            users.deleteById(userId);
            deletions.delete(userId);
            audit.record(new AuditLogger.AuditEvent(ACTION, target, "SUCCESS", null));
            return true;
        } catch (AccountDeletionBlockedException blocked) {
            // Aparecieron pedidos en curso entre la solicitud y el borrado: se devuelve la cuenta a la persona.
            credentials.setActive(userId, true);
            deletions.delete(userId);
            audit.record(new AuditLogger.AuditEvent(ACTION, target, "ABORTED", null));
            log.warn("Eliminacion de la cuenta {} abortada: {}", userId, blocked.getMessage());
            return false;
        } catch (RuntimeException e) {
            reschedule(current, e);
            return false;
        }
    }

    private void reschedule(AccountDeletion current, RuntimeException e) {
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (current.attempts() + 1 >= maxAttempts) {
            deletions.save(current.failed(error));
            audit.record(new AuditLogger.AuditEvent(ACTION, String.valueOf(current.userId()), "FAILED", null));
            log.error("Eliminacion de la cuenta {} fallo tras {} intentos; requiere intervencion manual: {}",
                    current.userId(), current.attempts() + 1, error);
            return;
        }
        Duration backoff = Duration.ofMinutes(1L << Math.min(current.attempts(), 6));
        if (backoff.compareTo(MAX_BACKOFF) > 0) {
            backoff = MAX_BACKOFF;
        }
        Instant next = clock.instant().plus(backoff);
        deletions.save(current.retryAt(next, error));
        log.warn("Eliminacion de la cuenta {} pendiente, reintento en {}: {}", current.userId(), backoff, error);
    }
}

package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.TransactionalRunner;
import com.flashdrop.auth.domain.exception.ResourceAlreadyExistsException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wraps multi-repository application workflows in a single Spring-managed transaction.
 *
 * <p>Fails fast when no transaction manager is available: running the block
 * without a transaction would leave {@code users.email} and {@code login.login}
 * able to diverge, which is exactly what this port exists to prevent.
 *
 * <p>A unique-constraint violation is translated into a domain exception so the
 * application layer never has to know about Spring's persistence exceptions.
 */
@Component
public class SpringTransactionalRunner implements TransactionalRunner {

    private final ObjectProvider<PlatformTransactionManager> txManagerProvider;

    public SpringTransactionalRunner(ObjectProvider<PlatformTransactionManager> txManagerProvider) {
        this.txManagerProvider = txManagerProvider;
    }

    @Override
    public void run(Runnable action) {
        PlatformTransactionManager txManager = txManagerProvider.getIfAvailable();
        if (txManager == null) {
            throw new IllegalStateException(
                    "No hay PlatformTransactionManager disponible: el cambio de email exige una transaccion");
        }
        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> action.run());
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException();
        }
    }
}

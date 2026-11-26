package com.flashdrop.auth.application.port.outbound;

/**
 * Executes an operation inside a single atomic transaction boundary
 * without leaking framework transaction annotations into application/.
 */
public interface TransactionalRunner {
    void run(Runnable action);
}

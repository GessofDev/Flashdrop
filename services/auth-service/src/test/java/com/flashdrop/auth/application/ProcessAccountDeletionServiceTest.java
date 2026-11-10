package com.flashdrop.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.flashdrop.auth.application.port.outbound.AccountDeletionStore;
import com.flashdrop.auth.application.port.outbound.AccountErasurePort;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.ProcessAccountDeletionService;
import com.flashdrop.auth.domain.exception.AccountDeletionBlockedException;
import com.flashdrop.auth.domain.exception.AccountErasureUnavailableException;
import com.flashdrop.auth.domain.model.AccountDeletion;
import com.flashdrop.auth.domain.model.AccountDeletion.Status;

/** F5: el borrado en segundo plano se reanuda, reintenta y nunca deja el proceso a medias sin registro. */
class ProcessAccountDeletionServiceTest {

    private static final Long USER = 42L;
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    private final AccountDeletionStore deletions = mock(AccountDeletionStore.class);
    private final AccountErasurePort erasure = mock(AccountErasurePort.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CredentialStore credentials = mock(CredentialStore.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final ProcessAccountDeletionService service = new ProcessAccountDeletionService(
            deletions, erasure, users, credentials, audit, Clock.fixed(NOW, ZoneOffset.UTC), 5);

    private static AccountDeletion pending(boolean orders, boolean delivery, boolean catalog, int attempts) {
        return new AccountDeletion(USER, Status.PENDING, attempts, orders, delivery, catalog, NOW, null);
    }

    private void due(AccountDeletion deletion) {
        when(deletions.findDue(NOW, 20)).thenReturn(List.of(deletion));
    }

    @Test
    void processDue_runsOrdersThenDeliveryThenCatalog_andFinallyDeletesTheUserAndTheRequest() {
        due(pending(false, false, false, 0));

        int completed = service.processDue();

        assertEquals(1, completed);
        InOrder order = inOrder(erasure, users, deletions);
        order.verify(erasure).eraseOrders(USER);
        order.verify(erasure).eraseDelivery(USER);
        order.verify(erasure).eraseCatalog(USER);
        order.verify(users).deleteById(USER);
        order.verify(deletions).delete(USER);
    }

    @Test
    void processDue_resumesWhereItLeftOff_withoutRepeatingFinishedSteps() {
        due(pending(true, true, false, 3));

        service.processDue();

        verify(erasure, never()).eraseOrders(any());
        verify(erasure, never()).eraseDelivery(any());
        verify(erasure).eraseCatalog(USER);
        verify(users).deleteById(USER);
    }

    @Test
    void processDue_whenDeliveryFails_keepsTheProgress_schedulesARetry_andDoesNotDeleteTheUser() {
        due(pending(false, false, false, 0));
        doThrow(new AccountErasureUnavailableException("Delivery no disponible")).when(erasure).eraseDelivery(USER);

        int completed = service.processDue();

        assertEquals(0, completed);
        ArgumentCaptor<AccountDeletion> saved = forClass(AccountDeletion.class);
        verify(deletions, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        AccountDeletion last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertTrue(last.ordersDone(), "el paso de Orders ya hecho se conserva");
        assertFalse(last.deliveryDone());
        assertEquals(1, last.attempts());
        assertEquals(NOW.plus(Duration.ofMinutes(1)), last.nextAttemptAt());
        assertEquals(Status.PENDING, last.status());
        verify(users, never()).deleteById(any());
        verify(deletions, never()).delete(any());
    }

    @Test
    void processDue_backoffDoublesEachAttempt() {
        due(pending(false, false, false, 3));
        doThrow(new AccountErasureUnavailableException("x")).when(erasure).eraseOrders(USER);

        service.processDue();

        ArgumentCaptor<AccountDeletion> saved = forClass(AccountDeletion.class);
        verify(deletions).save(saved.capture());
        assertEquals(NOW.plus(Duration.ofMinutes(8)), saved.getValue().nextAttemptAt());
    }

    @Test
    void processDue_backoffIsCappedAtOneHour() {
        ProcessAccountDeletionService patient = new ProcessAccountDeletionService(
                deletions, erasure, users, credentials, audit, Clock.fixed(NOW, ZoneOffset.UTC), 50);
        due(pending(false, false, false, 10));
        doThrow(new AccountErasureUnavailableException("x")).when(erasure).eraseOrders(USER);

        patient.processDue();

        ArgumentCaptor<AccountDeletion> saved = forClass(AccountDeletion.class);
        verify(deletions).save(saved.capture());
        assertEquals(NOW.plus(Duration.ofHours(1)), saved.getValue().nextAttemptAt());
    }

    @Test
    void processDue_afterTheMaximumAttempts_marksItFailedForManualFollowUp() {
        due(pending(false, false, false, 4));
        doThrow(new AccountErasureUnavailableException("Orders no disponible")).when(erasure).eraseOrders(USER);

        service.processDue();

        ArgumentCaptor<AccountDeletion> saved = forClass(AccountDeletion.class);
        verify(deletions).save(saved.capture());
        assertEquals(Status.FAILED, saved.getValue().status());
        verify(users, never()).deleteById(any());
    }

    @Test
    void processDue_whenOrdersFindsNewActiveOrders_abortsAndGivesTheAccountBack() {
        due(pending(false, false, false, 0));
        doThrow(new AccountDeletionBlockedException("Aparecieron pedidos")).when(erasure).eraseOrders(USER);

        int completed = service.processDue();

        assertEquals(0, completed);
        verify(credentials).setActive(USER, true);
        verify(deletions).delete(USER);
        verify(users, never()).deleteById(any());
        verify(erasure, never()).eraseDelivery(any());
    }

    @Test
    void processDue_withNothingDue_doesNothing() {
        when(deletions.findDue(NOW, 20)).thenReturn(List.of());

        assertEquals(0, service.processDue());
        verify(erasure, never()).eraseOrders(any());
    }
}

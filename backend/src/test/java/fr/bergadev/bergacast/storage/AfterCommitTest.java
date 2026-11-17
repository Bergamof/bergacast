package fr.bergadev.bergacast.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    private final List<String> calls = new ArrayList<>();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runsTheActionRightAwayOutsideOfATransaction() {
        AfterCommit.run(() -> calls.add("ran"));

        assertThat(calls).containsExactly("ran");
    }

    @Test
    void runsTheActionOnlyOnceTheTransactionIsCommitted() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> calls.add("ran"));

        assertThat(calls).isEmpty();
        synchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(calls).containsExactly("ran");
    }

    @Test
    void neverRunsTheActionWhenTheTransactionIsRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> calls.add("ran"));
        synchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(calls).isEmpty();
    }

    @Test
    void runsTheActionsInTheOrderTheyWereRegistered() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> calls.add("first"));
        AfterCommit.run(() -> calls.add("second"));
        synchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void queuesTheActionUntilTheScopeIsClosedSoThatTheConnectionIsReleasedFirst() {
        TransactionSynchronizationManager.initSynchronization();
        assertThat(AfterCommit.openScope()).isTrue();

        AfterCommit.run(() -> calls.add("ran"));
        synchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(calls).isEmpty();
        AfterCommit.closeScope();
        assertThat(calls).containsExactly("ran");
    }

    @Test
    void leavesTheOutermostScopeToRunTheQueue() {
        assertThat(AfterCommit.openScope()).isTrue();

        assertThat(AfterCommit.openScope()).isFalse();

        AfterCommit.closeScope();
        assertThat(AfterCommit.openScope()).isTrue();
        AfterCommit.closeScope();
    }

    @Test
    void forgetsTheActionsOfARolledBackTransactionInAScope() {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.openScope();

        AfterCommit.run(() -> calls.add("ran"));
        synchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        AfterCommit.closeScope();

        assertThat(calls).isEmpty();
    }

    @Test
    void closesTheScopeEvenWhenAnActionFails() {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.openScope();
        AfterCommit.run(() -> {
            throw new IllegalStateException("storage down");
        });
        synchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThatThrownBy(AfterCommit::closeScope).isInstanceOf(IllegalStateException.class);

        assertThat(AfterCommit.openScope()).isTrue();
        AfterCommit.closeScope();
    }

    private static List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}

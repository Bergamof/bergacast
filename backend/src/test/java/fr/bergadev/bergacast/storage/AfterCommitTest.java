package fr.bergadev.bergacast.storage;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}

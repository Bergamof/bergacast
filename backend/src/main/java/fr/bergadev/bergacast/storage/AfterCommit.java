package fr.bergadev.bergacast.storage;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers changes to the object storage until the database changes they reflect are committed, so that a rollback
 * never leaves the public objects ahead of the database.
 */
public final class AfterCommit {

    private AfterCommit() {}

    /**
     * Runs {@code action} once the current transaction is committed, or right away outside of a transaction. An
     * exception thrown by the action reaches the caller that committed the transaction, which is committed already.
     */
    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

package fr.bergadev.bergacast.storage;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers changes to the object storage until the database changes they reflect are committed, so that a rollback
 * never leaves the public objects ahead of the database.
 *
 * <p>Spring calls {@code afterCommit} before it gives the connection of the transaction back to the pool: a storage
 * call made there keeps a connection (and a serverless database awake) during network I/O. Inside an
 * {@link AfterCommitScope}, the actions are therefore queued and run once the outermost call of the scope has
 * returned, when the transaction is over and its connection released.
 */
public final class AfterCommit {

    private static final ThreadLocal<List<Runnable>> QUEUE = new ThreadLocal<>();

    private AfterCommit() {}

    /**
     * Runs {@code action} once the current transaction is committed (and, inside an {@link AfterCommitScope}, once
     * the scope is left), or right away outside of a transaction. An exception thrown by the action reaches the
     * caller that committed the transaction, which is committed already.
     */
    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                List<Runnable> queue = QUEUE.get();
                if (queue == null) {
                    action.run();
                } else {
                    queue.add(action);
                }
            }
        });
    }

    /** Opens a scope on the current thread; returns {@code false} when one is open already (the outermost runs). */
    static boolean openScope() {
        if (QUEUE.get() != null) {
            return false;
        }
        QUEUE.set(new ArrayList<>());
        return true;
    }

    /**
     * Closes the scope opened by {@link #openScope()} and runs the queued actions in order.
     *
     * @throws RuntimeException the first failure of an action; the following actions are not run
     */
    static void closeScope() {
        List<Runnable> queue = QUEUE.get();
        QUEUE.remove();
        queue.forEach(Runnable::run);
    }
}

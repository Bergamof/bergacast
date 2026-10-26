package fr.bergadev.bergacast.requestlog;

/**
 * Tells whether the current thread borrowed a database connection since it started tracking, to find out which HTTP
 * requests wake up the database. Connections borrowed while no tracking is in progress (startup) are ignored.
 */
final class ConnectionUsage {

    private static final ThreadLocal<Boolean> CONNECTION_BORROWED = new ThreadLocal<>();

    private ConnectionUsage() {}

    static void startTracking() {
        CONNECTION_BORROWED.set(Boolean.FALSE);
    }

    static void recordConnectionBorrowed() {
        if (CONNECTION_BORROWED.get() != null) {
            CONNECTION_BORROWED.set(Boolean.TRUE);
        }
    }

    /** Ends the tracking of the current thread and tells whether it borrowed a connection meanwhile. */
    static boolean stopTracking() {
        boolean borrowed = Boolean.TRUE.equals(CONNECTION_BORROWED.get());
        CONNECTION_BORROWED.remove();
        return borrowed;
    }
}

package fr.bergadev.bergacast.migration;

/** A migration could not be applied: the changesets before the failing one stay applied. */
public final class MigrationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    MigrationFailedException(Throwable cause) {
        super("Migration failed: " + cause.getMessage(), cause);
    }
}

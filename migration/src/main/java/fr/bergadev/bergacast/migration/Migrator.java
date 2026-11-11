package fr.bergadev.bergacast.migration;

import java.sql.Connection;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

/** Applies the changesets of the changelog that the database has not run yet. */
public final class Migrator {

    static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    /**
     * Migrates the database behind the connection, which is closed afterwards.
     *
     * @throws MigrationFailedException when a changeset fails: the changesets before it stay applied
     */
    public void migrate(Connection connection) {
        try {
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
        } catch (LiquibaseException | UnexpectedLiquibaseException e) {
            throw new MigrationFailedException(e);
        }
    }
}

package fr.bergadev.bergacast.migration;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/** Entry point of the migration job: migrates the database, then exits with a status the platform reads. */
public final class MigrationMain {

    static final int SUCCESS = 0;
    static final int FAILURE = 1;

    private static final System.Logger LOGGER = System.getLogger(MigrationMain.class.getName());

    private MigrationMain() {}

    public static void main(String[] arguments) {
        System.exit(run(System.getenv()));
    }

    static int run(Map<String, String> environment) {
        try {
            DatabaseSettings settings = DatabaseSettings.fromEnvironment(environment);
            LOGGER.log(System.Logger.Level.INFO, "Migrating {0}", settings);
            new Migrator()
                    .migrate(DriverManager.getConnection(settings.url(), settings.username(), settings.password()));
            LOGGER.log(System.Logger.Level.INFO, "Database is up to date");
            return SUCCESS;
        } catch (IllegalArgumentException | SQLException | MigrationFailedException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Migration failed: " + e.getMessage(), e);
            return FAILURE;
        }
    }
}

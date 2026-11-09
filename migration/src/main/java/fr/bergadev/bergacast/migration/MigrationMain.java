package fr.bergadev.bergacast.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Entry point of the migration job: migrates the database, creates the initial administrator when there is none, then
 * exits with a status the platform reads.
 */
public final class MigrationMain {

    public static final int SUCCESS = 0;
    public static final int FAILURE = 1;

    private static final System.Logger LOGGER = System.getLogger(MigrationMain.class.getName());

    private MigrationMain() {}

    public static void main(String[] arguments) {
        System.exit(run(System.getenv()));
    }

    /**
     * Runs the job with the given environment (the variables of the job), as {@link #main} does with the real one.
     *
     * @return {@link #SUCCESS} or {@link #FAILURE}
     */
    public static int run(Map<String, String> environment) {
        try {
            DatabaseSettings settings = DatabaseSettings.fromEnvironment(environment);
            Optional<InitialAdministrator> administrator = InitialAdministrator.fromEnvironment(environment);
            LOGGER.log(System.Logger.Level.INFO, "Migrating {0}", settings);
            new Migrator().migrate(connect(settings));
            LOGGER.log(System.Logger.Level.INFO, "Database is up to date");
            try (Connection connection = connect(settings)) {
                ensureAnAdministrator(connection, administrator);
            }
            return SUCCESS;
        } catch (IllegalArgumentException | SQLException | MigrationFailedException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Migration failed: " + e.getMessage(), e);
            return FAILURE;
        }
    }

    private static Connection connect(DatabaseSettings settings) throws SQLException {
        return DriverManager.getConnection(settings.url(), settings.username(), settings.password());
    }

    private static void ensureAnAdministrator(Connection connection, Optional<InitialAdministrator> administrator)
            throws SQLException {
        AdministratorAccounts accounts = new AdministratorAccounts(Clock.systemUTC());
        if (administrator.isPresent()) {
            if (accounts.createIfNone(connection, administrator.get())) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "Created the initial administrator ''{0}''",
                        administrator.get().username());
            }
        } else if (!accounts.anyExists(connection)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "No administrator exists and {0} / {1} are not set: nobody can log in to the administration",
                    InitialAdministrator.USERNAME_VARIABLE,
                    InitialAdministrator.PASSWORD_VARIABLE);
        }
    }
}

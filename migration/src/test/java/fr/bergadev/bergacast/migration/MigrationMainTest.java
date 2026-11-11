package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigrationMainTest {

    @Test
    void exitsWithSuccessOnceTheDatabaseIsMigrated() throws SQLException {
        String url = PostgresDatabases.createDatabase();

        assertThat(MigrationMain.run(environment(url))).isEqualTo(MigrationMain.SUCCESS);
    }

    @Test
    void exitsWithFailureWhenTheSettingsAreIncomplete() {
        assertThat(MigrationMain.run(Map.of())).isEqualTo(MigrationMain.FAILURE);
    }

    @Test
    void exitsWithFailureWhenTheDatabaseIsUnreachable() {
        assertThat(MigrationMain.run(environment("jdbc:postgresql://localhost:1/unreachable")))
                .isEqualTo(MigrationMain.FAILURE);
    }

    @Test
    void createsTheInitialAdministratorOnce() throws SQLException {
        String url = PostgresDatabases.createDatabase();
        Map<String, String> environment = withAdministrator(environment(url), "admin", "a-long-enough-password");

        assertThat(MigrationMain.run(environment)).isEqualTo(MigrationMain.SUCCESS);
        assertThat(MigrationMain.run(environment)).isEqualTo(MigrationMain.SUCCESS);

        assertThat(administrators(url)).containsExactly("admin");
    }

    @Test
    void keepsTheAdministratorsAlreadyThere() throws SQLException {
        String url = PostgresDatabases.createDatabase();
        MigrationMain.run(withAdministrator(environment(url), "first", "a-long-enough-password"));

        MigrationMain.run(withAdministrator(environment(url), "second", "another-long-password"));

        assertThat(administrators(url)).containsExactly("first");
    }

    @Test
    void migratesWithoutAdministratorWhenNoneIsConfigured() throws SQLException {
        String url = PostgresDatabases.createDatabase();

        assertThat(MigrationMain.run(environment(url))).isEqualTo(MigrationMain.SUCCESS);

        assertThat(administrators(url)).isEmpty();
    }

    @Test
    void failsBeforeTouchingTheDatabaseWhenTheAdministratorIsInvalid() throws SQLException {
        String url = PostgresDatabases.createDatabase();

        assertThat(MigrationMain.run(withAdministrator(environment(url), "admin", "short")))
                .isEqualTo(MigrationMain.FAILURE);

        try (Connection connection = PostgresDatabases.connect(url);
                Statement statement = connection.createStatement();
                ResultSet tables =
                        statement.executeQuery("SELECT count(*) FROM pg_tables WHERE tablename = 'podcast'")) {
            tables.next();
            assertThat(tables.getLong(1)).isZero();
        }
    }

    private static Map<String, String> withAdministrator(
            Map<String, String> environment, String user, String password) {
        Map<String, String> result = new HashMap<>(environment);
        result.put(InitialAdministrator.USERNAME_VARIABLE, user);
        result.put(InitialAdministrator.PASSWORD_VARIABLE, password);
        return result;
    }

    private static List<String> administrators(String url) throws SQLException {
        List<String> usernames = new ArrayList<>();
        try (Connection connection = PostgresDatabases.connect(url);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT username FROM admin_user ORDER BY created_at")) {
            while (result.next()) {
                usernames.add(result.getString(1));
            }
        }
        return usernames;
    }

    private static Map<String, String> environment(String url) {
        return Map.of(
                DatabaseSettings.URL_VARIABLE, url,
                DatabaseSettings.USERNAME_VARIABLE, PostgresDatabases.username(),
                DatabaseSettings.PASSWORD_VARIABLE, PostgresDatabases.password());
    }
}

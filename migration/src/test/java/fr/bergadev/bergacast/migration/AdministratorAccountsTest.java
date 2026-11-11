package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class AdministratorAccountsTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final String PASSWORD = "a-long-enough-password";

    private final AdministratorAccounts accounts = new AdministratorAccounts(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsTheAdministratorOfADatabaseWithNone() throws Exception {
        try (Connection connection = migratedDatabase()) {
            assertThat(accounts.anyExists(connection)).isFalse();

            boolean created = accounts.createIfNone(connection, new InitialAdministrator("admin", PASSWORD));

            assertThat(created).isTrue();
            assertThat(accounts.anyExists(connection)).isTrue();
            assertThat(value(connection, "SELECT username FROM admin_user")).isEqualTo("admin");
            assertThat(value(connection, "SELECT created_at FROM admin_user")).startsWith("2026-11-09 12:00:00");
        }
    }

    /** What the backend checks at login: the delegating encoder reads the {bcrypt} prefix. */
    @Test
    void storesAHashTheBackendAcceptsAtLogin() throws Exception {
        try (Connection connection = migratedDatabase()) {
            accounts.createIfNone(connection, new InitialAdministrator("admin", PASSWORD));

            String hash = value(connection, "SELECT password_hash FROM admin_user");

            assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
            assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                            .matches(PASSWORD, hash))
                    .isTrue();
        }
    }

    @Test
    void leavesTheAdministratorsOfADatabaseThatHasOne() throws Exception {
        try (Connection connection = migratedDatabase()) {
            accounts.createIfNone(connection, new InitialAdministrator("first", PASSWORD));

            boolean created = accounts.createIfNone(connection, new InitialAdministrator("second", PASSWORD));

            assertThat(created).isFalse();
            assertThat(value(connection, "SELECT string_agg(username, ',') FROM admin_user"))
                    .isEqualTo("first");
        }
    }

    private static Connection migratedDatabase() throws SQLException {
        String url = PostgresDatabases.createDatabase();
        new Migrator().migrate(PostgresDatabases.connect(url));
        return PostgresDatabases.connect(url);
    }

    private static String value(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}

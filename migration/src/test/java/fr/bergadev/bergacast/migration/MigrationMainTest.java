package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
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

    private static Map<String, String> environment(String url) {
        return Map.of(
                DatabaseSettings.URL_VARIABLE, url,
                DatabaseSettings.USERNAME_VARIABLE, PostgresDatabases.username(),
                DatabaseSettings.PASSWORD_VARIABLE, PostgresDatabases.password());
    }
}

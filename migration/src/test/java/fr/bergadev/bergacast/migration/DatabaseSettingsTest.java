package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseSettingsTest {

    private static final Map<String, String> COMPLETE = Map.of(
            DatabaseSettings.URL_VARIABLE, "jdbc:postgresql://db/bergacast",
            DatabaseSettings.USERNAME_VARIABLE, "migrator",
            DatabaseSettings.PASSWORD_VARIABLE, "s3cret");

    @Test
    void readsTheConnectionFromTheEnvironment() {
        assertThat(DatabaseSettings.fromEnvironment(COMPLETE))
                .isEqualTo(new DatabaseSettings("jdbc:postgresql://db/bergacast", "migrator", "s3cret"));
    }

    @Test
    void namesEveryMissingVariable() {
        assertThatThrownBy(() -> DatabaseSettings.fromEnvironment(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(DatabaseSettings.URL_VARIABLE)
                .hasMessageContaining(DatabaseSettings.USERNAME_VARIABLE)
                .hasMessageContaining(DatabaseSettings.PASSWORD_VARIABLE);
    }

    @Test
    void treatsABlankVariableAsMissing() {
        Map<String, String> blankPassword = Map.of(
                DatabaseSettings.URL_VARIABLE, "jdbc:postgresql://db/bergacast",
                DatabaseSettings.USERNAME_VARIABLE, "migrator",
                DatabaseSettings.PASSWORD_VARIABLE, " ");

        assertThatThrownBy(() -> DatabaseSettings.fromEnvironment(blankPassword))
                .hasMessageContaining(DatabaseSettings.PASSWORD_VARIABLE)
                .hasMessageNotContaining(DatabaseSettings.URL_VARIABLE);
    }

    @Test
    void neverPrintsThePassword() {
        assertThat(DatabaseSettings.fromEnvironment(COMPLETE).toString())
                .contains("jdbc:postgresql://db/bergacast")
                .contains("migrator")
                .doesNotContain("s3cret");
    }
}

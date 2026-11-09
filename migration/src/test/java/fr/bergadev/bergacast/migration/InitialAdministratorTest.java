package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InitialAdministratorTest {

    private static final String USERNAME = InitialAdministrator.USERNAME_VARIABLE;
    private static final String PASSWORD = InitialAdministrator.PASSWORD_VARIABLE;

    @Test
    void readsTheAdministratorFromTheEnvironment() {
        assertThat(InitialAdministrator.fromEnvironment(Map.of(USERNAME, "admin", PASSWORD, "a-long-enough-password")))
                .contains(new InitialAdministrator("admin", "a-long-enough-password"));
    }

    @Test
    void hasNoAdministratorToCreateWhenNeitherVariableIsSet() {
        assertThat(InitialAdministrator.fromEnvironment(Map.of())).isEmpty();
        assertThat(InitialAdministrator.fromEnvironment(Map.of(USERNAME, "", PASSWORD, " ")))
                .isEmpty();
    }

    @Test
    void refusesOneVariableWithoutTheOther() {
        assertThatThrownBy(() -> InitialAdministrator.fromEnvironment(Map.of(USERNAME, "admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("go together");
        assertThatThrownBy(() -> InitialAdministrator.fromEnvironment(Map.of(PASSWORD, "a-long-enough-password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("go together");
    }

    @Test
    void refusesAPasswordShorterThanTheBackendAccepts() {
        assertThatThrownBy(() -> new InitialAdministrator("admin", "short"))
                .hasMessageContaining(PASSWORD)
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    void acceptsAPasswordOfExactlyTheMinimumLength() {
        assertThat(new InitialAdministrator("admin", "x".repeat(12)).password()).hasSize(12);
    }

    @Test
    void refusesAPasswordThatBcryptWouldTruncate() {
        assertThatThrownBy(() -> new InitialAdministrator("admin", "x".repeat(73)))
                .hasMessageContaining("at most 72 bytes");
        // 37 two-byte characters: 37 characters, 74 bytes.
        assertThatThrownBy(() -> new InitialAdministrator("admin", "é".repeat(37)))
                .hasMessageContaining("at most 72 bytes");
    }

    @Test
    void refusesAnEmptyOrTooLongUsername() {
        assertThatThrownBy(() -> new InitialAdministrator(" ", "a-long-enough-password"))
                .hasMessageContaining(USERNAME);
        assertThatThrownBy(() -> new InitialAdministrator("u".repeat(101), "a-long-enough-password"))
                .hasMessageContaining(USERNAME);
    }

    @Test
    void neverPrintsThePassword() {
        assertThat(new InitialAdministrator("admin", "a-long-enough-password").toString())
                .contains("admin")
                .doesNotContain("a-long-enough-password");
    }
}

package fr.bergadev.bergacast.migration;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * The administrator to create when the database has none, read from the environment of the job: {@code
 * BERGACAST_ADMIN_USERNAME} and {@code BERGACAST_ADMIN_PASSWORD}.
 */
public record InitialAdministrator(String username, String password) {

    static final String USERNAME_VARIABLE = "BERGACAST_ADMIN_USERNAME";
    static final String PASSWORD_VARIABLE = "BERGACAST_ADMIN_PASSWORD";
    static final int MAX_USERNAME_LENGTH = 100;
    static final int MIN_PASSWORD_LENGTH = 12;
    // bcrypt ignores, and Spring Security rejects, anything longer.
    static final int MAX_PASSWORD_BYTES = 72;

    public InitialAdministrator {
        if (username.isBlank() || username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException(
                    USERNAME_VARIABLE + " must contain 1 to " + MAX_USERNAME_LENGTH + " characters");
        }
        if (password.codePointCount(0, password.length()) < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(PASSWORD_VARIABLE + " must contain at least " + MIN_PASSWORD_LENGTH
                    + " characters and at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    /**
     * @return the administrator to create, empty when neither variable is set
     * @throws IllegalArgumentException when only one of them is set, or a value breaks the rules of the backend
     */
    public static Optional<InitialAdministrator> fromEnvironment(Map<String, String> environment) {
        String username = environment.getOrDefault(USERNAME_VARIABLE, "");
        String password = environment.getOrDefault(PASSWORD_VARIABLE, "");
        if (username.isBlank() && password.isBlank()) {
            return Optional.empty();
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException(USERNAME_VARIABLE + " and " + PASSWORD_VARIABLE + " go together");
        }
        return Optional.of(new InitialAdministrator(username, password));
    }

    @Override
    public String toString() {
        return "InitialAdministrator[username=" + username + ", password=<redacted>]";
    }
}

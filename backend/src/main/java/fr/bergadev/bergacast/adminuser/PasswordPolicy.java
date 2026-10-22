package fr.bergadev.bergacast.adminuser;

import java.nio.charset.StandardCharsets;

/**
 * Password rules: at least {@value #MIN_LENGTH} characters, and at most {@value #MAX_BYTES} bytes once UTF-8 encoded
 * (bcrypt ignores, and Spring Security rejects, anything longer).
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_BYTES = 72;
    static final String DESCRIPTION =
            "must contain at least " + MIN_LENGTH + " characters and at most " + MAX_BYTES + " bytes";

    private PasswordPolicy() {}

    static boolean isSatisfiedBy(String password) {
        return password.codePointCount(0, password.length()) >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}

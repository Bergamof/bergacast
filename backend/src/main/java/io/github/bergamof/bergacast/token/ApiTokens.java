package io.github.bergamof.bergacast.token;

import io.github.bergamof.bergacast.web.Sha256;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Generation and hashing of plain API tokens ({@code bgc_} followed by 32 random bytes in base64url). */
public final class ApiTokens {

    public static final String PREFIX = "bgc_";
    static final int RANDOM_BYTES = 32;
    static final int DISPLAYED_PREFIX_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ApiTokens() {}

    public static boolean looksLikeApiToken(String bearerToken) {
        return bearerToken.startsWith(PREFIX);
    }

    static String generate() {
        byte[] random = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(random);
        return PREFIX + ENCODER.encodeToString(random);
    }

    /**
     * Hashes a plain token for storage and lookup. A fast unsalted hash is enough: tokens carry 256 bits of entropy,
     * so they cannot be brute-forced, and a deterministic hash allows an indexed lookup.
     */
    static String hash(String plainToken) {
        return Sha256.hex(plainToken.getBytes(StandardCharsets.UTF_8));
    }

    /** First characters of the token, displayed to help identify it. */
    static String displayedPrefix(String plainToken) {
        return plainToken.substring(0, DISPLAYED_PREFIX_LENGTH);
    }
}

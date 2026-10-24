package fr.bergadev.bergacast.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings bound from {@code bergacast.*} (see {@code application.yaml} for the environment variables).
 *
 * @param publicBaseUrl absolute base URL used to build public links (feed, audio, publish endpoint)
 * @param jwt settings of the admin access tokens
 * @param admin optional initial administrator created when no administrator exists
 * @param cors cross-origin access to the API, needed by the admin SPA served from another origin
 */
@ConfigurationProperties("bergacast")
public record BergacastProperties(
        @DefaultValue("http://localhost:8080") URI publicBaseUrl,
        Jwt jwt,
        @DefaultValue InitialAdmin admin,
        @DefaultValue Cors cors) {

    public BergacastProperties {
        if (jwt == null) {
            throw new IllegalArgumentException("bergacast.jwt.secret (BERGACAST_JWT_SECRET) is required");
        }
        publicBaseUrl = URI.create(withoutTrailingSlash(publicBaseUrl.toString()));
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * @param secret HMAC secret, at least {@value #MIN_SECRET_BYTES} bytes once UTF-8 encoded (HS256 requirement)
     * @param lifetime validity of an issued access token
     * @param issuer value of the {@code iss} claim
     */
    public record Jwt(
            String secret,
            @DefaultValue("15m") Duration lifetime,
            @DefaultValue("bergacast") String issuer) {

        public static final int MIN_SECRET_BYTES = 32;

        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException("bergacast.jwt.secret (BERGACAST_JWT_SECRET) must be at least "
                        + MIN_SECRET_BYTES + " bytes long");
            }
        }

        public byte[] secretBytes() {
            return secret.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return "Jwt[secret=<redacted>, lifetime=" + lifetime + ", issuer=" + issuer + "]";
        }
    }

    /**
     * @param username username of the administrator to create at startup, may be blank
     * @param password password of the administrator to create at startup, may be blank
     */
    public record InitialAdmin(String username, String password) {

        public boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "InitialAdmin[username=" + username + ", password=<redacted>]";
        }
    }

    /** @param allowedOrigins origins (scheme, host and port) allowed to call the API from a browser, none by default */
    public record Cors(@DefaultValue List<String> allowedOrigins) {

        public Cors {
            allowedOrigins = List.copyOf(allowedOrigins);
        }
    }
}

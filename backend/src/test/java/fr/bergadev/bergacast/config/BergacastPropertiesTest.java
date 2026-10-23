package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BergacastPropertiesTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void requiresAJwtSecret() {
        assertThatThrownBy(() -> new BergacastProperties(URI.create("http://localhost"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BERGACAST_JWT_SECRET");
    }

    @Test
    void requiresAJwtSecretOfAtLeast32Bytes() {
        assertThatThrownBy(() -> new BergacastProperties.Jwt(SECRET.substring(1), Duration.ofMinutes(15), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void removesTheTrailingSlashOfTheBaseUrl() {
        BergacastProperties properties = new BergacastProperties(
                URI.create("https://example.test/"),
                new BergacastProperties.Jwt(SECRET, Duration.ofMinutes(15), "x"),
                new BergacastProperties.InitialAdmin(null, null),
                new BergacastProperties.Cors(List.of()));

        assertThat(properties.publicBaseUrl()).hasToString("https://example.test");
        assertThat(properties.admin().isConfigured()).isFalse();
        assertThat(properties.jwt().toString()).doesNotContain(SECRET);
    }

    @Test
    void copiesTheAllowedCorsOriginsDefensively() {
        List<String> origins = new ArrayList<>(List.of("https://admin.example.test"));

        BergacastProperties.Cors cors = new BergacastProperties.Cors(origins);
        origins.add("https://evil.example.test");

        assertThat(cors.allowedOrigins()).containsExactly("https://admin.example.test");
        assertThatThrownBy(() -> cors.allowedOrigins().add("https://other.example.test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

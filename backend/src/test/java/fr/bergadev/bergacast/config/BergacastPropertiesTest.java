package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class BergacastPropertiesTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String STORAGE_SECRET_KEY = "storage-secret-key-value";
    private static final URI PUBLIC_BASE_URL = URI.create("https://podcasts.example.test");
    private static final URI MEDIA_BASE_URL = URI.create("https://media.example.test");
    private static final URI STORAGE_ENDPOINT = URI.create("https://s3.fr-par.scw.cloud");
    private static final DataSize IMAGE_LIMIT = DataSize.ofMegabytes(20);
    private static final BergacastProperties.Jwt JWT = new BergacastProperties.Jwt(SECRET, Duration.ofMinutes(15), "x");
    private static final BergacastProperties.Storage STORAGE =
            new BergacastProperties.Storage(STORAGE_ENDPOINT, "fr-par", "bucket", "access-key", STORAGE_SECRET_KEY);

    @Test
    void requiresAJwtSecret() {
        assertThatThrownBy(() -> new BergacastProperties(
                        PUBLIC_BASE_URL, MEDIA_BASE_URL, STORAGE, null, null, null, IMAGE_LIMIT))
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
    void removesTheTrailingSlashOfTheBaseUrls() {
        BergacastProperties properties = properties(URI.create("https://example.test/"), URI.create("https://m.test/"));

        assertThat(properties.publicBaseUrl()).hasToString("https://example.test");
        assertThat(properties.mediaBaseUrl()).hasToString("https://m.test");
        assertThat(properties.admin().isConfigured()).isFalse();
        assertThat(properties.jwt().toString()).doesNotContain(SECRET);
    }

    @Test
    void requiresAMediaBaseUrl() {
        assertThatThrownBy(() -> properties(PUBLIC_BASE_URL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BERGACAST_MEDIA_BASE_URL");
    }

    @Test
    void requiresAnAbsoluteMediaBaseUrl() {
        assertThatThrownBy(() -> properties(PUBLIC_BASE_URL, URI.create("/media")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BERGACAST_MEDIA_BASE_URL")
                .hasMessageContaining("absolute");
    }

    /** The backend redirects the former media URLs to the media base URL: the same URL would loop. */
    @Test
    void requiresAMediaBaseUrlDifferentFromThePublicBaseUrl() {
        assertThatThrownBy(() -> properties(URI.create("https://example.test"), URI.create("https://example.test/")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ from bergacast.public-base-url");
    }

    @Test
    void requiresTheObjectStorageSettings() {
        assertThatThrownBy(() -> new BergacastProperties(
                        PUBLIC_BASE_URL,
                        MEDIA_BASE_URL,
                        null,
                        JWT,
                        new BergacastProperties.InitialAdmin(null, null),
                        new BergacastProperties.Cors(List.of()),
                        IMAGE_LIMIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BERGACAST_STORAGE_");
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "null, bucket, access, secret, BERGACAST_STORAGE_REGION",
                "' ', bucket, access, secret, BERGACAST_STORAGE_REGION",
                "fr-par, null, access, secret, BERGACAST_STORAGE_BUCKET",
                "fr-par, '', access, secret, BERGACAST_STORAGE_BUCKET",
                "fr-par, bucket, null, secret, BERGACAST_STORAGE_ACCESS_KEY",
                "fr-par, bucket, ' ', secret, BERGACAST_STORAGE_ACCESS_KEY",
                "fr-par, bucket, access, null, BERGACAST_STORAGE_SECRET_KEY",
                "fr-par, bucket, access, '', BERGACAST_STORAGE_SECRET_KEY"
            })
    void requiresEveryObjectStorageSetting(
            @Nullable String region,
            @Nullable String bucket,
            @Nullable String accessKey,
            @Nullable String secretKey,
            String environmentVariable) {
        assertThatThrownBy(
                        () -> new BergacastProperties.Storage(STORAGE_ENDPOINT, region, bucket, accessKey, secretKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(environmentVariable)
                .hasMessageContaining("is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "s3.fr-par.scw.cloud", "/s3"})
    void requiresAnAbsoluteObjectStorageEndpoint(String endpoint) {
        assertThatThrownBy(() ->
                        new BergacastProperties.Storage(URI.create(endpoint), "fr-par", "bucket", "access", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BERGACAST_STORAGE_ENDPOINT");
    }

    @Test
    void requiresAnObjectStorageEndpoint() {
        assertThatThrownBy(() -> new BergacastProperties.Storage(null, "fr-par", "bucket", "access", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BERGACAST_STORAGE_ENDPOINT");
    }

    @Test
    void redactsTheObjectStorageSecretKey() {
        assertThat(STORAGE.toString())
                .doesNotContain(STORAGE_SECRET_KEY)
                .contains("secretKey=<redacted>", "accessKey=access-key", "bucket=bucket", STORAGE_ENDPOINT.toString());
        assertThat(properties(PUBLIC_BASE_URL, MEDIA_BASE_URL).toString()).doesNotContain(STORAGE_SECRET_KEY);
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

    @ParameterizedTest
    @ValueSource(strings = {"0B", "2GB"})
    void requiresAnImageUploadLimitBetweenOneByteAndOneGigabyte(String limit) {
        assertThatThrownBy(() -> new BergacastProperties(
                        PUBLIC_BASE_URL,
                        MEDIA_BASE_URL,
                        STORAGE,
                        JWT,
                        new BergacastProperties.InitialAdmin(null, null),
                        new BergacastProperties.Cors(List.of()),
                        DataSize.parse(limit)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BERGACAST_MAX_IMAGE_UPLOAD_SIZE");
    }

    private static BergacastProperties properties(URI publicBaseUrl, @Nullable URI mediaBaseUrl) {
        return new BergacastProperties(
                publicBaseUrl,
                mediaBaseUrl,
                STORAGE,
                JWT,
                new BergacastProperties.InitialAdmin(null, null),
                new BergacastProperties.Cors(List.of()),
                IMAGE_LIMIT);
    }
}

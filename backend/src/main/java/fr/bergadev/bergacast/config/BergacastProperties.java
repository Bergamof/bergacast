package fr.bergadev.bergacast.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Application settings bound from {@code bergacast.*} (see {@code application.yaml} for the environment variables).
 *
 * @param publicBaseUrl absolute base URL of the backend, used to build links to the API (e.g. the publish endpoint)
 * @param mediaBaseUrl absolute public base URL of the object storage bucket (or of a CDN in front of it), used to
 *     build the public links to feeds, audio files and images
 * @param storage object storage where feeds, audio files and images are written
 * @param jwt settings of the admin access tokens
 * @param admin optional initial administrator created when no administrator exists
 * @param cors cross-origin access to the API, needed by the admin SPA served from another origin
 * @param maxImageUploadSize largest podcast cover image accepted for upload, before any downscaling
 */
@ConfigurationProperties("bergacast")
public record BergacastProperties(
        @DefaultValue("http://localhost:8080") URI publicBaseUrl,
        URI mediaBaseUrl,
        Storage storage,
        Jwt jwt,
        @DefaultValue InitialAdmin admin,
        @DefaultValue Cors cors,
        @DefaultValue("20MB") DataSize maxImageUploadSize) {

    /** Uploaded images are held in a byte array, hence an upper bound below 2 GiB. */
    public static final DataSize MAX_IMAGE_UPLOAD_SIZE_LIMIT = DataSize.ofGigabytes(1);

    public BergacastProperties {
        if (jwt == null) {
            throw new IllegalArgumentException("bergacast.jwt.secret (BERGACAST_JWT_SECRET) is required");
        }
        if (maxImageUploadSize.toBytes() <= 0 || maxImageUploadSize.compareTo(MAX_IMAGE_UPLOAD_SIZE_LIMIT) > 0) {
            throw new IllegalArgumentException("bergacast.max-image-upload-size (BERGACAST_MAX_IMAGE_UPLOAD_SIZE) must "
                    + "be positive and at most " + MAX_IMAGE_UPLOAD_SIZE_LIMIT);
        }
        if (mediaBaseUrl == null || !mediaBaseUrl.isAbsolute()) {
            throw new IllegalArgumentException(
                    "bergacast.media-base-url (BERGACAST_MEDIA_BASE_URL) must be an absolute URL");
        }
        if (storage == null) {
            throw new IllegalArgumentException("bergacast.storage (BERGACAST_STORAGE_*) is required");
        }
        publicBaseUrl = URI.create(withoutTrailingSlash(publicBaseUrl.toString()));
        mediaBaseUrl = URI.create(withoutTrailingSlash(mediaBaseUrl.toString()));
        if (publicBaseUrl.equals(mediaBaseUrl)) {
            // The backend redirects its former media URLs to the media base URL: the same URL would loop.
            throw new IllegalArgumentException(
                    "bergacast.media-base-url (BERGACAST_MEDIA_BASE_URL) must differ from bergacast.public-base-url");
        }
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
     * S3-compatible object storage (Scaleway Object Storage in production), addressed with path-style URLs.
     *
     * @param endpoint URL of the S3 API, e.g. {@code https://s3.fr-par.scw.cloud}
     * @param region region of the bucket, e.g. {@code fr-par}
     * @param bucket name of the bucket
     * @param accessKey access key of the credentials allowed to write to the bucket
     * @param secretKey secret key of these credentials
     */
    public record Storage(
            URI endpoint, @DefaultValue("fr-par") String region, String bucket, String accessKey, String secretKey) {

        public Storage {
            requireText(region, "region");
            requireText(bucket, "bucket");
            requireText(accessKey, "access-key");
            requireText(secretKey, "secret-key");
            if (endpoint == null || !endpoint.isAbsolute()) {
                throw new IllegalArgumentException(
                        "bergacast.storage.endpoint (BERGACAST_STORAGE_ENDPOINT) must be an absolute URL");
            }
        }

        private static void requireText(String value, String property) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("bergacast.storage." + property + " (BERGACAST_STORAGE_"
                        + property.replace('-', '_').toUpperCase(Locale.ROOT) + ") is required");
            }
        }

        @Override
        public String toString() {
            return "Storage[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + ", accessKey="
                    + accessKey + ", secretKey=<redacted>]";
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

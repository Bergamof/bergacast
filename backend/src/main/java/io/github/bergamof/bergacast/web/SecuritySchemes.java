package io.github.bergamof.bergacast.web;

/** Names of the OpenAPI security schemes, referenced by the controllers' documentation. */
public final class SecuritySchemes {

    /** Short-lived JWT obtained from {@code POST /api/v1/access-tokens}, granting full administration rights. */
    public static final String ADMIN_JWT = "adminJwt";

    /** Long-lived {@code bgc_...} token allowing an automated tool to publish episodes of one podcast. */
    public static final String PODCAST_TOKEN = "podcastToken";

    private SecuritySchemes() {}
}

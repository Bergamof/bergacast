package io.github.bergamof.bergacast.web;

/** Media types used by the REST API in addition to the ones of {@link org.springframework.http.MediaType}. */
public final class ApiMediaTypes {

    /** JSON Merge Patch (RFC 7396). */
    public static final String MERGE_PATCH_JSON_VALUE = "application/merge-patch+json";

    private ApiMediaTypes() {}
}

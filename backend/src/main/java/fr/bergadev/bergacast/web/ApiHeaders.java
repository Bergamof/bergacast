package fr.bergadev.bergacast.web;

/** HTTP headers used by the REST API in addition to the ones of {@link org.springframework.http.HttpHeaders}. */
public final class ApiHeaders {

    /** Makes an episode upload safe to retry (IETF draft {@code Idempotency-Key} header). */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private ApiHeaders() {}
}

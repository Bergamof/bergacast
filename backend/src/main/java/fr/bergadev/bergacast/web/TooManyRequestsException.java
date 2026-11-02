package fr.bergadev.bergacast.web;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** The client must wait before trying again (HTTP 429), for the duration given in {@code Retry-After}. */
public final class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String detail, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, detail);
        getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(wholeSecondsRoundedUp(retryAfter)));
    }

    /** Rounded up, so that a client retrying exactly then is no longer refused. */
    private static long wholeSecondsRoundedUp(Duration duration) {
        long seconds = duration.toSeconds();
        return duration.equals(Duration.ofSeconds(seconds)) ? seconds : seconds + 1;
    }
}

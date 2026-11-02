package fr.bergadev.bergacast.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class TooManyRequestsExceptionTest {

    @Test
    void answers429WithTheDetail() {
        TooManyRequestsException exception = new TooManyRequestsException("Wait", Duration.ofSeconds(10));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exception.getBody().getDetail()).isEqualTo("Wait");
    }

    @Test
    void givesRetryAfterInWholeSeconds() {
        assertThat(retryAfter(Duration.ofSeconds(10))).isEqualTo("10");
    }

    @Test
    void roundsRetryAfterUp() {
        assertThat(retryAfter(Duration.ofMillis(9_001))).isEqualTo("10");
    }

    private static String retryAfter(Duration duration) {
        return new TooManyRequestsException("Wait", duration).getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
    }
}

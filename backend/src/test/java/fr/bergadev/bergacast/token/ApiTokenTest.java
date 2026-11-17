package fr.bergadev.bergacast.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.bergadev.bergacast.podcast.Podcast;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiTokenTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private final ApiToken token = new ApiToken(mock(Podcast.class), "CI", "hash", "bgc_abcd", CREATED);

    @Test
    void recordsTheFirstUse() {
        token.markUsed(CREATED);

        assertThat(token.getLastUsedAt()).isEqualTo(CREATED);
    }

    @Test
    void doesNotWriteAgainWithinThePrecision() {
        token.markUsed(CREATED);

        token.markUsed(CREATED.plus(ApiToken.LAST_USE_PRECISION).minusSeconds(1));

        assertThat(token.getLastUsedAt()).isEqualTo(CREATED);
    }

    @Test
    void recordsTheUseOnceThePrecisionHasElapsed() {
        token.markUsed(CREATED);
        Instant later = CREATED.plus(ApiToken.LAST_USE_PRECISION).plus(Duration.ofSeconds(1));

        token.markUsed(later);

        assertThat(token.getLastUsedAt()).isEqualTo(later);
    }
}

package fr.bergadev.bergacast.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.bergadev.bergacast.web.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private static final String USERNAME = "admin";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-11-02T10:00:00Z"));
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void allowsTheFirstFailuresWithoutDelay() {
        throttle.recordFailure(USERNAME);
        throttle.recordFailure(USERNAME);

        assertThatCode(() -> throttle.checkAllowed(USERNAME)).doesNotThrowAnyException();
    }

    @Test
    void blocksTenSecondsAtTheThirdConsecutiveFailure() {
        failTimes(3);

        assertBlockedFor(10);
        clock.advance(Duration.ofSeconds(9));
        assertBlockedFor(1);
        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> throttle.checkAllowed(USERNAME)).doesNotThrowAnyException();
    }

    @Test
    void doublesTheBlockAtEachFurtherFailure() {
        failTimes(3);
        clock.advance(Duration.ofSeconds(10));
        throttle.recordFailure(USERNAME);
        assertBlockedFor(20);

        clock.advance(Duration.ofSeconds(20));
        throttle.recordFailure(USERNAME);
        assertBlockedFor(40);
    }

    @Test
    void capsTheBlockAtOneHour() {
        assertThat(LoginThrottle.blockFor(3)).isEqualTo(Duration.ofSeconds(10));
        assertThat(LoginThrottle.blockFor(11)).isEqualTo(Duration.ofSeconds(2560));
        assertThat(LoginThrottle.blockFor(12)).isEqualTo(Duration.ofHours(1));
        assertThat(LoginThrottle.blockFor(Integer.MAX_VALUE)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void forgetsTheFailuresAfterASuccessfulLogin() {
        failTimes(2);
        throttle.recordSuccess(USERNAME);
        failTimes(2);

        assertThatCode(() -> throttle.checkAllowed(USERNAME)).doesNotThrowAnyException();
    }

    @Test
    void throttlesEachUsernameOnItsOwn() {
        failTimes(3);

        assertThatCode(() -> throttle.checkAllowed("another-admin")).doesNotThrowAnyException();
    }

    @Test
    void forgetsTheLeastRecentlyUsedUsernamesBeyondItsCapacity() {
        failTimes(3);
        for (int index = 0; index < LoginThrottle.MAX_TRACKED_USERNAMES; index++) {
            throttle.recordFailure("guess-" + index);
        }

        assertThatCode(() -> throttle.checkAllowed(USERNAME)).doesNotThrowAnyException();
        assertThatCode(() -> throttle.checkAllowed("guess-0")).doesNotThrowAnyException();
    }

    private void failTimes(int count) {
        for (int attempt = 0; attempt < count; attempt++) {
            throttle.recordFailure(USERNAME);
        }
    }

    private void assertBlockedFor(long seconds) {
        assertThatThrownBy(() -> throttle.checkAllowed(USERNAME))
                .isInstanceOfSatisfying(
                        TooManyRequestsException.class,
                        exception -> assertThat(exception.getHeaders().getFirst("Retry-After"))
                                .isEqualTo(Long.toString(seconds)));
    }

    /** Fixed clock that the test moves forward. */
    private static final class AdjustableClock extends Clock {

        private Instant now;

        AdjustableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

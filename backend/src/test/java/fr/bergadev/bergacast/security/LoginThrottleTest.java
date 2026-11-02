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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private static final String USERNAME = "admin";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-11-02T10:00:00Z"));
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void allowsTheFirstFailuresWithoutDelay() {
        failTimes(2);

        assertAllowed(USERNAME);
    }

    @Test
    void blocksTenSecondsAtTheThirdConsecutiveFailure() {
        failTimes(3);

        assertBlockedFor(10);
        clock.advance(Duration.ofSeconds(9));
        assertBlockedFor(1);
        clock.advance(Duration.ofSeconds(1));
        assertAllowed(USERNAME);
    }

    @Test
    void doublesTheBlockAtEachFurtherFailure() {
        failTimes(3);
        clock.advance(Duration.ofSeconds(10));
        failTimes(1);
        assertBlockedFor(20);

        clock.advance(Duration.ofSeconds(20));
        failTimes(1);
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
        throttle.begin(USERNAME).succeeded();
        failTimes(2);

        assertAllowed(USERNAME);
    }

    @Test
    void doesNotCountAnAttemptWhosePasswordWasNotChecked() {
        failTimes(2);
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.begin(USERNAME).close();
        }

        assertAllowed(USERNAME);
    }

    @Test
    void concludesAnAttemptOnlyOnce() {
        LoginThrottle.LoginAttempt attempt = throttle.begin(USERNAME);
        attempt.failed();
        attempt.failed();
        attempt.close();
        failTimes(1);

        assertAllowed(USERNAME);
    }

    @Test
    void neverChecksMoreSimultaneousAttemptsThanTheFreeOnes() {
        List<LoginThrottle.LoginAttempt> inProgress = new ArrayList<>();
        for (int attempt = 0; attempt < LoginThrottle.FREE_ATTEMPTS; attempt++) {
            inProgress.add(throttle.begin(USERNAME));
        }

        assertThatThrownBy(() -> throttle.begin(USERNAME))
                .isInstanceOfSatisfying(
                        TooManyRequestsException.class,
                        exception -> assertThat(exception.getHeaders().getFirst("Retry-After"))
                                .isEqualTo("1"));
        inProgress.forEach(LoginThrottle.LoginAttempt::failed);
        assertBlockedFor(10);
    }

    @Test
    void checksASingleAttemptAtATimeOnceTheFreeOnesAreUsedUp() {
        failTimes(3);
        clock.advance(Duration.ofSeconds(10));
        LoginThrottle.LoginAttempt inProgress = throttle.begin(USERNAME);

        assertThatThrownBy(() -> throttle.begin(USERNAME)).isInstanceOf(TooManyRequestsException.class);
        inProgress.succeeded();
        assertAllowed(USERNAME);
    }

    @Test
    void throttlesEachUsernameOnItsOwn() {
        failTimes(3);

        assertAllowed("another-admin");
    }

    @Test
    void forgetsTheLeastRecentlyUsedUsernamesBeyondItsCapacityButNeverABlock() {
        failTimes(3);
        throttle.begin("old-guess").failed();
        for (int index = 0; index < LoginThrottle.MAX_TRACKED_USERNAMES; index++) {
            throttle.begin("guess-" + index).failed();
        }

        assertBlockedFor(10);
        LoginThrottle.LoginAttempt forgotten = throttle.begin("old-guess");
        forgotten.failed();
        throttle.begin("old-guess").failed();
        assertAllowed("old-guess");
    }

    private void failTimes(int count) {
        for (int attempt = 0; attempt < count; attempt++) {
            throttle.begin(USERNAME).failed();
        }
    }

    private void assertAllowed(String username) {
        assertThatCode(() -> throttle.begin(username).close()).doesNotThrowAnyException();
    }

    private void assertBlockedFor(long seconds) {
        assertThatThrownBy(() -> throttle.begin(USERNAME))
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

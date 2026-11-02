package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.web.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing on the administrator login, per username: after {@value #FREE_ATTEMPTS} consecutive
 * failures the username is blocked for {@link #FIRST_BLOCK}, a block doubled at each further failure (up to
 * {@link #LONGEST_BLOCK}) until a successful login resets it. Attempts made while blocked are refused without checking
 * the password (no hashing cost) and do not extend the block. Unknown usernames are throttled the same way, so that
 * the answers do not tell which usernames exist.
 *
 * <p>An attempt is reserved before the password is checked ({@link #begin}): simultaneous requests cannot exceed the
 * free attempts, and once they are used up, a single attempt at a time is checked.
 *
 * <p>Kept in memory: the backend runs as a single instance, and a restart only forgets the blocks. Beyond
 * {@value #MAX_TRACKED_USERNAMES} usernames, the least recently used ones are forgotten first, except those blocked or
 * being checked, so that guesses on random usernames can neither exhaust the memory nor lift a block.
 */
@Component
class LoginThrottle {

    static final int FREE_ATTEMPTS = 3;
    static final Duration FIRST_BLOCK = Duration.ofSeconds(10);
    static final Duration LONGEST_BLOCK = Duration.ofHours(1);
    static final int MAX_TRACKED_USERNAMES = 10_000;
    /** Suggested wait when an attempt for the same username is still being checked. */
    static final Duration ATTEMPT_IN_PROGRESS_WAIT = Duration.ofSeconds(1);

    /** Beyond this many doublings, the block is anyway capped by {@link #LONGEST_BLOCK}. */
    private static final int MAX_DOUBLINGS = 20;

    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private static final String DETAIL = "Too many failed logins for this username: wait before trying again";

    private final Clock clock;
    /** Access-ordered: iteration starts with the least recently used username. */
    private final Map<String, Attempts> attemptsByUsername = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true);

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /**
     * Reserves a login attempt for {@code username}, to conclude with {@link LoginAttempt#failed()},
     * {@link LoginAttempt#succeeded()} or, when the password could not be checked, {@link LoginAttempt#close()}.
     *
     * @throws TooManyRequestsException while the username is blocked, or while it has used up its free attempts and
     *     another attempt is being checked
     */
    synchronized LoginAttempt begin(String username) {
        Attempts attempts = attemptsByUsername.getOrDefault(username, Attempts.NONE);
        Duration blockRemaining = Duration.between(clock.instant(), attempts.blockedUntil());
        if (blockRemaining.isPositive()) {
            throw new TooManyRequestsException(DETAIL, blockRemaining);
        }
        if (attempts.inProgress() > 0 && attempts.failures() + attempts.inProgress() >= FREE_ATTEMPTS) {
            throw new TooManyRequestsException(DETAIL, ATTEMPT_IN_PROGRESS_WAIT);
        }
        attemptsByUsername.put(username, attempts.withInProgress(attempts.inProgress() + 1));
        forgetLeastRecentlyUsedBeyondCapacity();
        return new LoginAttempt(username);
    }

    /** {@link #FIRST_BLOCK} at the {@value #FREE_ATTEMPTS}rd failure, doubled at each following one, capped. */
    static Duration blockFor(int failureCount) {
        int doublings = Math.min(failureCount - FREE_ATTEMPTS, MAX_DOUBLINGS);
        Duration block = FIRST_BLOCK.multipliedBy(1L << doublings);
        return block.compareTo(LONGEST_BLOCK) > 0 ? LONGEST_BLOCK : block;
    }

    private synchronized void conclude(String username, Outcome outcome) {
        Attempts attempts = attemptsByUsername.getOrDefault(username, Attempts.NONE);
        int inProgress = Math.max(attempts.inProgress() - 1, 0);
        Attempts concluded = switch (outcome) {
            case FAILED -> {
                int failures = attempts.failures() + 1;
                Instant blockedUntil =
                        failures < FREE_ATTEMPTS ? Instant.MIN : clock.instant().plus(blockFor(failures));
                yield new Attempts(failures, blockedUntil, inProgress);
            }
            case SUCCEEDED -> new Attempts(0, Instant.MIN, inProgress);
            case NOT_CHECKED -> attempts.withInProgress(inProgress);
        };
        if (concluded.equals(Attempts.NONE)) {
            attemptsByUsername.remove(username);
        } else {
            attemptsByUsername.put(username, concluded);
        }
    }

    private void forgetLeastRecentlyUsedBeyondCapacity() {
        Instant now = clock.instant();
        Iterator<Attempts> leastRecentlyUsedFirst = attemptsByUsername.values().iterator();
        while (attemptsByUsername.size() > MAX_TRACKED_USERNAMES && leastRecentlyUsedFirst.hasNext()) {
            Attempts attempts = leastRecentlyUsedFirst.next();
            if (attempts.inProgress() == 0 && !attempts.blockedUntil().isAfter(now)) {
                leastRecentlyUsedFirst.remove();
            }
        }
    }

    private enum Outcome {
        FAILED,
        SUCCEEDED,
        NOT_CHECKED
    }

    /**
     * Consecutive failures of a username, the end of its current block ({@link Instant#MIN} when none) and the number
     * of its attempts being checked.
     */
    private record Attempts(int failures, Instant blockedUntil, int inProgress) {

        static final Attempts NONE = new Attempts(0, Instant.MIN, 0);

        Attempts withInProgress(int count) {
            return new Attempts(failures, blockedUntil, count);
        }
    }

    /** A reserved attempt; concluding it more than once has no effect. */
    final class LoginAttempt implements AutoCloseable {

        private final String username;
        private boolean concluded;

        private LoginAttempt(String username) {
            this.username = username;
        }

        void failed() {
            concludeOnce(Outcome.FAILED);
        }

        void succeeded() {
            concludeOnce(Outcome.SUCCEEDED);
        }

        /** Releases the attempt when it was neither a failure nor a success (e.g. the database was unavailable). */
        @Override
        public void close() {
            concludeOnce(Outcome.NOT_CHECKED);
        }

        private void concludeOnce(Outcome outcome) {
            if (!concluded) {
                concluded = true;
                conclude(username, outcome);
            }
        }
    }
}

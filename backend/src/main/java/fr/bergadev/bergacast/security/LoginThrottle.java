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
 * <p>Kept in memory: the backend runs as a single instance, and a restart only forgets the blocks. At most
 * {@value #MAX_TRACKED_USERNAMES} usernames are tracked, the least recently used being forgotten first, so that
 * guesses on random usernames cannot exhaust the memory.
 */
@Component
class LoginThrottle {

    static final int FREE_ATTEMPTS = 3;
    static final Duration FIRST_BLOCK = Duration.ofSeconds(10);
    static final Duration LONGEST_BLOCK = Duration.ofHours(1);
    static final int MAX_TRACKED_USERNAMES = 10_000;

    /** Beyond this many doublings, the block is anyway capped by {@link #LONGEST_BLOCK}. */
    private static final int MAX_DOUBLINGS = 20;

    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private final Clock clock;
    /** Access-ordered: iteration starts with the least recently used username. */
    private final Map<String, Failures> failuresByUsername = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true);

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /**
     * Lets a login attempt for {@code username} go on.
     *
     * @throws TooManyRequestsException while the username is blocked, with the time left to wait
     */
    synchronized void checkAllowed(String username) {
        Failures failures = failuresByUsername.get(username);
        if (failures == null) {
            return;
        }
        Duration remaining = Duration.between(clock.instant(), failures.blockedUntil());
        if (remaining.isPositive()) {
            throw new TooManyRequestsException(
                    "Too many failed logins for this username: wait before trying again", remaining);
        }
    }

    synchronized void recordFailure(String username) {
        int count = failuresByUsername.getOrDefault(username, Failures.NONE).count() + 1;
        Instant blockedUntil =
                count < FREE_ATTEMPTS ? Instant.MIN : clock.instant().plus(blockFor(count));
        failuresByUsername.put(username, new Failures(count, blockedUntil));
        forgetLeastRecentlyUsedBeyondCapacity();
    }

    synchronized void recordSuccess(String username) {
        failuresByUsername.remove(username);
    }

    /** {@link #FIRST_BLOCK} at the {@value #FREE_ATTEMPTS}rd failure, doubled at each following one, capped. */
    static Duration blockFor(int failureCount) {
        int doublings = Math.min(failureCount - FREE_ATTEMPTS, MAX_DOUBLINGS);
        Duration block = FIRST_BLOCK.multipliedBy(1L << doublings);
        return block.compareTo(LONGEST_BLOCK) > 0 ? LONGEST_BLOCK : block;
    }

    private void forgetLeastRecentlyUsedBeyondCapacity() {
        Iterator<String> leastRecentlyUsedFirst = failuresByUsername.keySet().iterator();
        while (failuresByUsername.size() > MAX_TRACKED_USERNAMES) {
            leastRecentlyUsedFirst.next();
            leastRecentlyUsedFirst.remove();
        }
    }

    /** Consecutive failures of a username, and the end of its current block ({@link Instant#MIN} when none). */
    private record Failures(int count, Instant blockedUntil) {

        static final Failures NONE = new Failures(0, Instant.MIN);
    }
}

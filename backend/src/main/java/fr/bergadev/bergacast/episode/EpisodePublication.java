package fr.bergadev.bergacast.episode;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Publication of episodes, safe against concurrent retries with the same idempotency key.
 *
 * <p>Deliberately not transactional: when two requests race with the same key, the loser's transaction fails on the
 * unique constraint and is rolled back (discarding its audio large object), then the winner's episode is read in a
 * new transaction and returned as a replay.
 */
@Service
class EpisodePublication {

    static final String IDEMPOTENCY_KEY_CONSTRAINT = "episode_idempotency_key_unique";

    private final EpisodeService episodeService;

    EpisodePublication(EpisodeService episodeService) {
        this.episodeService = episodeService;
    }

    Episode publish(String slug, EpisodeInput input, MultipartFile audio, @Nullable String idempotencyKey) {
        try {
            return episodeService.publish(slug, input, audio, idempotencyKey);
        } catch (DataIntegrityViolationException conflict) {
            if (idempotencyKey == null || !violates(conflict, IDEMPOTENCY_KEY_CONSTRAINT)) {
                throw conflict;
            }
            return episodeService.findByIdempotencyKey(slug, idempotencyKey).orElseThrow(() -> conflict);
        }
    }

    static boolean violates(Throwable exception, String constraintName) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}

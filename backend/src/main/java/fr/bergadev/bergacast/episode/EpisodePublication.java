package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.audio.AudioFile;
import fr.bergadev.bergacast.audio.AudioUploads;
import fr.bergadev.bergacast.feed.FeedPublicationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Publication of episodes: the audio file is written to the object storage, then the episode is saved.
 *
 * <p>Deliberately not transactional: no database connection is held while the audio file is uploaded, and when the
 * episode cannot be saved its audio object is removed. When two requests race with the same idempotency key, the
 * loser's transaction fails on the unique constraint, then the winner's episode is read in a new transaction and
 * returned as a replay.
 */
@Service
class EpisodePublication {

    static final String IDEMPOTENCY_KEY_CONSTRAINT = "episode_idempotency_key_unique";

    private final EpisodeService episodeService;
    private final AudioUploads audioUploads;
    private final Clock clock;

    EpisodePublication(EpisodeService episodeService, AudioUploads audioUploads, Clock clock) {
        this.episodeService = episodeService;
        this.audioUploads = audioUploads;
        this.clock = clock;
    }

    Episode publish(String slug, EpisodeInput input, MultipartFile audio, @Nullable String idempotencyKey) {
        Instant now = clock.instant();
        EpisodeMetadata metadata = input.toMetadata(now);
        Optional<Episode> alreadyPublished = episodeService.prepare(slug, metadata, idempotencyKey);
        if (alreadyPublished.isPresent()) {
            return alreadyPublished.get();
        }
        UUID episodeId = UUID.randomUUID();
        AudioFile audioFile = audioUploads.upload(slug, episodeId, audio);
        Episode published;
        try {
            published = episodeService.publish(slug, episodeId, metadata, audioFile, now, idempotencyKey);
        } catch (FeedPublicationException feedNotWritten) {
            // The episode is committed: only its feed is behind, the audio object stays.
            throw feedNotWritten;
        } catch (RuntimeException notSaved) {
            audioUploads.discard(slug, episodeId, audioFile.getFileExtension());
            if (idempotencyKey == null || !violates(notSaved, IDEMPOTENCY_KEY_CONSTRAINT)) {
                throw notSaved;
            }
            return episodeService.findByIdempotencyKey(slug, idempotencyKey).orElseThrow(() -> notSaved);
        }
        if (!published.getId().equals(episodeId)) {
            // Replay of an episode published meanwhile with the same idempotency key: the new upload is not used.
            audioUploads.discard(slug, episodeId, audioFile.getFileExtension());
        }
        return published;
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

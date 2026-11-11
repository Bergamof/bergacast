package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.audio.AudioFile;
import fr.bergadev.bergacast.audio.AudioUploads;
import fr.bergadev.bergacast.feed.FeedPublicationException;
import fr.bergadev.bergacast.image.ProcessedImage;
import fr.bergadev.bergacast.podcast.PodcastFeedChanged;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Publication of episodes: the audio file and the optional image are written to the object storage, then the episode
 * is saved.
 *
 * <p>Deliberately not transactional: no database connection is held while the audio file is uploaded, and when the
 * episode cannot be saved its objects are removed. When two requests race with the same idempotency key, the
 * loser's transaction fails on the unique constraint, then the winner's episode is read in a new transaction and
 * returned as a replay. A replay writes the feed again: the request it repeats may have failed only because the feed
 * could not be written (503), and the retry must repair it.
 */
@Service
class EpisodePublication {

    static final String IDEMPOTENCY_KEY_CONSTRAINT = "episode_idempotency_key_unique";

    private final EpisodeService episodeService;
    private final AudioUploads audioUploads;
    private final EpisodeImages episodeImages;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EpisodePublication(
            EpisodeService episodeService,
            AudioUploads audioUploads,
            EpisodeImages episodeImages,
            ApplicationEventPublisher events,
            Clock clock) {
        this.episodeService = episodeService;
        this.audioUploads = audioUploads;
        this.episodeImages = episodeImages;
        this.events = events;
        this.clock = clock;
    }

    Episode publish(
            String slug,
            EpisodeInput input,
            MultipartFile audio,
            @Nullable MultipartFile imagePart,
            @Nullable String idempotencyKey) {
        Instant now = clock.instant();
        EpisodeMetadata metadata = input.toMetadata(now);
        Optional<Episode> alreadyPublished = episodeService.prepare(slug, metadata, idempotencyKey);
        if (alreadyPublished.isPresent()) {
            return replay(slug, alreadyPublished.get());
        }
        // Checked before the audio is uploaded: a bad image must not cost an upload of up to 500 MB.
        ProcessedImage image = hasContent(imagePart) ? episodeImages.fitPart(imagePart) : null;
        UUID episodeId = UUID.randomUUID();
        AudioFile audioFile = audioUploads.upload(slug, episodeId, audio);
        EpisodeImage storedImage = storeImage(slug, episodeId, audioFile, image);
        Episode published;
        try {
            published = episodeService.publish(slug, episodeId, metadata, audioFile, storedImage, now, idempotencyKey);
        } catch (FeedPublicationException feedNotWritten) {
            // The episode is committed: only its feed is behind, the objects stay.
            throw feedNotWritten;
        } catch (RuntimeException notSaved) {
            discard(slug, episodeId, audioFile, storedImage);
            if (idempotencyKey == null || !violates(notSaved, IDEMPOTENCY_KEY_CONSTRAINT)) {
                throw notSaved;
            }
            return replay(
                    slug,
                    episodeService.findByIdempotencyKey(slug, idempotencyKey).orElseThrow(() -> notSaved));
        }
        if (!published.getId().equals(episodeId)) {
            // Replay of an episode published meanwhile with the same idempotency key: the new upload is not used.
            discard(slug, episodeId, audioFile, storedImage);
            return replay(slug, published);
        }
        return published;
    }

    /** Writes the image, if any; when the storage fails, the audio file written just before is removed. */
    private @Nullable EpisodeImage storeImage(
            String slug, UUID episodeId, AudioFile audioFile, @Nullable ProcessedImage image) {
        if (image == null) {
            return null;
        }
        try {
            return episodeImages.store(slug, episodeId, image);
        } catch (RuntimeException imageNotStored) {
            audioUploads.discard(slug, episodeId, audioFile.getFileExtension());
            throw imageNotStored;
        }
    }

    /** Tools such as n8n send an empty file part when there is no image: it means no image, not an invalid one. */
    private static boolean hasContent(@Nullable MultipartFile part) {
        return part != null && !part.isEmpty();
    }

    private void discard(String slug, UUID episodeId, AudioFile audioFile, @Nullable EpisodeImage storedImage) {
        audioUploads.discard(slug, episodeId, audioFile.getFileExtension());
        if (storedImage != null) {
            episodeImages.discard(slug, episodeId, storedImage.format());
        }
    }

    /** Outside of any transaction, the feed is written right away; a failure is a {@link FeedPublicationException}. */
    private Episode replay(String slug, Episode alreadyPublished) {
        events.publishEvent(new PodcastFeedChanged(slug));
        return alreadyPublished;
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

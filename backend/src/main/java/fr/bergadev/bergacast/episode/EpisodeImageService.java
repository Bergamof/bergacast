package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.image.ProcessedImage;
import fr.bergadev.bergacast.podcast.PodcastFeedChanged;
import fr.bergadev.bergacast.storage.AfterCommit;
import fr.bergadev.bergacast.web.EntityTags;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import fr.bergadev.bergacast.web.UnprocessableContentException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Use cases of episode cover images, whose content is an object of the storage. Changing the image of an episode
 * changes the episode (its version, hence its entity tag) and its feed, written again once the change is committed.
 */
@Service
class EpisodeImageService {

    private final EpisodeService episodeService;
    private final EpisodeRepository episodeRepository;
    private final EpisodeImages images;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;

    EpisodeImageService(
            EpisodeService episodeService,
            EpisodeRepository episodeRepository,
            EpisodeImages images,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.episodeService = episodeService;
        this.episodeRepository = episodeRepository;
        this.images = images;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * Returns the image of an episode.
     *
     * @throws ResourceNotFoundException when there is no such episode or it has no image
     */
    EpisodeImage get(String slug, UUID episodeId) {
        return current(slug, episodeId).orElseThrow(() -> noImage(episodeId));
    }

    /**
     * Checks that an image can be uploaded for an episode, before its content is read: the episode exists and the
     * optional {@code If-Match} header matches its current image.
     *
     * @throws ResourceNotFoundException when there is no such episode
     * @throws fr.bergadev.bergacast.web.PreconditionFailedException when {@code If-Match} does not match
     */
    void prepare(String slug, UUID episodeId, @Nullable String ifMatch) {
        requireIfMatch(current(slug, episodeId), ifMatch);
    }

    /**
     * Sets the image of an episode, downscaled if larger than what podcast apps accept. Checked and resized outside
     * of any transaction (no database connection is held meanwhile), then written to the storage once the
     * preconditions are checked again, before the change is committed.
     *
     * @throws UnprocessableContentException when the content is not a valid image of the declared format
     * @throws fr.bergadev.bergacast.storage.ObjectStorageException when the storage fails (nothing is changed)
     */
    EpisodeImageChange put(
            String slug, UUID episodeId, byte[] content, ImageFormat declaredFormat, @Nullable String ifMatch) {
        ProcessedImage image = images.fit(content, declaredFormat, UnprocessableContentException.BODY_FIELD);
        return Objects.requireNonNull(transaction.execute(status -> store(slug, episodeId, image, ifMatch)));
    }

    /**
     * Deletes the image of an episode; its object is removed from the storage once the deletion is committed.
     *
     * @throws ResourceNotFoundException when there is no such episode or it has no image
     */
    void delete(String slug, UUID episodeId) {
        transaction.executeWithoutResult(status -> {
            Episode episode = episodeService.get(slug, episodeId);
            EpisodeImage image = episode.getImage().orElseThrow(() -> noImage(episodeId));
            episode.setImage(null);
            // Flushed now so that the version (entity tag of the episode) is bumped inside this transaction.
            episodeRepository.saveAndFlush(episode);
            AfterCommit.run(() -> images.discard(slug, episodeId, image.format()));
            events.publishEvent(new PodcastFeedChanged(slug));
        });
    }

    private EpisodeImageChange store(String slug, UUID episodeId, ProcessedImage image, @Nullable String ifMatch) {
        Episode episode = episodeService.get(slug, episodeId);
        Optional<EpisodeImage> previous = episode.getImage();
        // Checked again: the image may have changed while the new one was being processed.
        requireIfMatch(previous, ifMatch);
        EpisodeImage stored = images.store(slug, episodeId, image);
        previous.map(EpisodeImage::format)
                .filter(previousFormat -> previousFormat != image.format())
                .ifPresent(previousFormat -> AfterCommit.run(() -> images.discard(slug, episodeId, previousFormat)));
        episode.setImage(stored);
        episodeRepository.saveAndFlush(episode);
        events.publishEvent(new PodcastFeedChanged(slug));
        return new EpisodeImageChange(stored, previous.isEmpty());
    }

    private Optional<EpisodeImage> current(String slug, UUID episodeId) {
        return Objects.requireNonNull(readOnlyTransaction.execute(
                status -> episodeService.get(slug, episodeId).getImage()));
    }

    private static void requireIfMatch(Optional<EpisodeImage> current, @Nullable String ifMatch) {
        EntityTags.requireIfMatch(ifMatch, current.map(EpisodeImage::entityTag));
    }

    private static ResourceNotFoundException noImage(UUID episodeId) {
        return new ResourceNotFoundException("The episode " + episodeId + " has no image");
    }

    /**
     * Result of {@link #put}.
     *
     * @param image the stored image
     * @param created whether the episode had no image before
     */
    record EpisodeImageChange(EpisodeImage image, boolean created) {}
}

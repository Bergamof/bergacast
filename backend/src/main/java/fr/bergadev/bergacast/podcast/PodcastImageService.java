package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.image.ImageResizer;
import fr.bergadev.bergacast.image.InvalidImageException;
import fr.bergadev.bergacast.image.ProcessedImage;
import fr.bergadev.bergacast.web.EntityTags;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import fr.bergadev.bergacast.web.Sha256;
import fr.bergadev.bergacast.web.UnprocessableContentException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Use cases of podcast cover images. Changing the image of a podcast changes the podcast representation, so it also
 * updates the podcast ({@code updatedAt} and version, hence its entity tag).
 */
@Service
public class PodcastImageService {

    /** Field reported in validation errors: the image is the whole request body. */
    static final String BODY_FIELD = "$";

    private final PodcastService podcastService;
    private final PodcastImageRepository imageRepository;
    private final ImageResizer resizer;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;

    PodcastImageService(
            PodcastService podcastService,
            PodcastImageRepository imageRepository,
            ImageResizer resizer,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.podcastService = podcastService;
        this.imageRepository = imageRepository;
        this.resizer = resizer;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * Returns the metadata of the image of a podcast, if it has one.
     *
     * @throws ResourceNotFoundException when there is no such podcast
     */
    public Optional<PodcastImageMetadata> find(String slug) {
        return Objects.requireNonNull(readOnlyTransaction.execute(status -> currentImage(slug)));
    }

    /** Returns the metadata of the image of a podcast, if it has one. */
    public Optional<PodcastImageMetadata> find(Podcast podcast) {
        return Objects.requireNonNull(
                readOnlyTransaction.execute(status -> imageRepository.findMetadata(podcast.getId())));
    }

    /**
     * Returns the metadata of the image of a podcast.
     *
     * @throws ResourceNotFoundException when there is no such podcast or it has no image
     */
    public PodcastImageMetadata get(String slug) {
        return find(slug).orElseThrow(() -> noImage(slug));
    }

    /**
     * Returns the content of an image, to serve it.
     *
     * @throws ResourceNotFoundException when the image has been deleted meanwhile
     */
    public byte[] content(String slug, PodcastImageMetadata image) {
        return Objects.requireNonNull(
                        readOnlyTransaction.execute(status -> imageRepository.findContent(image.podcastId())))
                .orElseThrow(() -> noImage(slug));
    }

    /**
     * Checks that an image can be uploaded for a podcast, before its content is read: the podcast exists and the
     * optional {@code If-Match} header matches its current image.
     *
     * @param ifMatch optional {@code If-Match} header value, checked against the entity tag of the current image
     * @throws ResourceNotFoundException when there is no such podcast
     * @throws fr.bergadev.bergacast.web.PreconditionFailedException when {@code If-Match} does not match
     */
    public void prepare(String slug, @Nullable String ifMatch) {
        requireIfMatch(find(slug), ifMatch);
    }

    /**
     * Sets the image of a podcast, downscaled if larger than what podcast apps accept. The preconditions checked by
     * {@link #prepare} are checked again when storing it, since the image may change meanwhile.
     *
     * <p>The image is checked and resized outside of any transaction: no database connection is held meanwhile.
     *
     * @param ifMatch optional {@code If-Match} header value, checked against the entity tag of the current image
     * @throws ResourceNotFoundException when there is no such podcast
     * @throws UnprocessableContentException when the content is not a valid image of the declared format
     */
    public PodcastImageChange put(String slug, byte[] content, ImageFormat declaredFormat, @Nullable String ifMatch) {
        ProcessedImage image = fit(content, declaredFormat);
        String sha256 = Sha256.hex(image.content());
        return Objects.requireNonNull(transaction.execute(status -> store(slug, image, sha256, ifMatch)));
    }

    /**
     * Deletes the image of a podcast.
     *
     * @throws ResourceNotFoundException when there is no such podcast or it has no image
     */
    public void delete(String slug) {
        transaction.executeWithoutResult(status -> {
            Podcast podcast = podcastService.getBySlug(slug);
            if (imageRepository.deleteByPodcastId(podcast.getId()) == 0) {
                throw noImage(slug);
            }
            podcast.markUpdated(clock.instant());
        });
    }

    private PodcastImageChange store(String slug, ProcessedImage image, String sha256, @Nullable String ifMatch) {
        Podcast podcast = podcastService.getBySlug(slug);
        Optional<PodcastImageMetadata> current = imageRepository.findMetadata(podcast.getId());
        // Checked again: the image may have changed while the new one was being processed.
        requireIfMatch(current, ifMatch);
        if (current.isPresent()) {
            imageRepository.deleteByPodcastId(podcast.getId());
        }
        PodcastImage stored = imageRepository.save(new PodcastImage(podcast.getId(), image, sha256));
        podcast.markUpdated(clock.instant());
        return new PodcastImageChange(stored.metadata(), current.isEmpty());
    }

    private Optional<PodcastImageMetadata> currentImage(String slug) {
        return imageRepository.findMetadata(podcastService.getBySlug(slug).getId());
    }

    private static void requireIfMatch(Optional<PodcastImageMetadata> current, @Nullable String ifMatch) {
        current.ifPresentOrElse(
                image -> EntityTags.requireIfMatch(ifMatch, image.entityTag()),
                () -> EntityTags.requireIfMatchOnMissingResource(ifMatch));
    }

    private ProcessedImage fit(byte[] content, ImageFormat declaredFormat) {
        try {
            return resizer.fit(content, declaredFormat);
        } catch (InvalidImageException invalid) {
            throw new UnprocessableContentException(BODY_FIELD, invalid.getMessage());
        }
    }

    private static ResourceNotFoundException noImage(String slug) {
        return new ResourceNotFoundException("The podcast '" + slug + "' has no image");
    }

    /**
     * Result of {@link #put}.
     *
     * @param image metadata of the stored image
     * @param created whether the podcast had no image before
     */
    public record PodcastImageChange(PodcastImageMetadata image, boolean created) {}
}

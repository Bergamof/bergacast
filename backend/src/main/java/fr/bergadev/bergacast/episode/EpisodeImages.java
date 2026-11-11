package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.image.ImageResizer;
import fr.bergadev.bergacast.image.InvalidImageException;
import fr.bergadev.bergacast.image.ProcessedImage;
import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.storage.ObjectStorage;
import fr.bergadev.bergacast.web.ContentTooLargeException;
import fr.bergadev.bergacast.web.Sha256;
import fr.bergadev.bergacast.web.UnprocessableContentException;
import fr.bergadev.bergacast.web.UnsupportedMediaTypeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checks, downscales and writes the cover images of episodes to the object storage. Called outside of any
 * transaction by the publication, which removes the object when the episode cannot be saved, and inside the one of
 * the image service, where (as for podcast images) an object written for a change that then fails to commit stays
 * behind as an unreferenced orphan.
 */
@Component
class EpisodeImages {

    /** Name of the optional multipart part of a publication holding the image. */
    static final String IMAGE_PART = "image";
    /** Field reported in validation errors when the image is the whole request body. */
    static final String BODY_FIELD = "$";
    /** Same freshness as the feed, which links the image: its URL stays the same when it is replaced. */
    private static final CacheControl FIVE_MINUTES =
            CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final ImageResizer resizer;
    private final ObjectStorage storage;

    EpisodeImages(ImageResizer resizer, ObjectStorage storage) {
        this.resizer = resizer;
        this.storage = storage;
    }

    /**
     * Reads and checks the image part of a publication.
     *
     * @throws UnsupportedMediaTypeException when the part is neither {@code image/jpeg} nor {@code image/png}
     * @throws ContentTooLargeException when the part is larger than {@code limit}
     * @throws UnprocessableContentException when the content is not a valid image of the declared type
     */
    ProcessedImage fit(MultipartFile part, String field, DataSize limit) {
        ImageFormat format = ImageFormat.fromContentType(part.getContentType())
                .orElseThrow(() -> new UnsupportedMediaTypeException(
                        "The " + field + " part must have an image/jpeg or image/png content type"));
        if (part.getSize() > limit.toBytes()) {
            throw new ContentTooLargeException("The " + field + " part exceeds the maximum size of " + limit);
        }
        try {
            return fit(part.getBytes(), format, field);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read the uploaded image", unreadable);
        }
    }

    /** @throws UnprocessableContentException when the content is not a valid image of the declared format */
    ProcessedImage fit(byte[] content, ImageFormat declaredFormat, String field) {
        try {
            return resizer.fit(content, declaredFormat);
        } catch (InvalidImageException invalid) {
            throw new UnprocessableContentException(field, invalid.getMessage());
        }
    }

    /**
     * Writes the image of an episode to the object storage and returns its description. If the episode is never
     * saved with it, the object must be removed with {@link #discard}.
     *
     * @throws fr.bergadev.bergacast.storage.ObjectStorageException when the storage fails
     */
    EpisodeImage store(String podcastSlug, UUID episodeId, ProcessedImage image) {
        ImageFormat format = image.format();
        storage.put(
                MediaKeys.episodeImage(podcastSlug, episodeId, format.fileExtension()),
                image.content(),
                format.mimeType(),
                FIVE_MINUTES);
        return new EpisodeImage(
                format.mimeType(), image.width(), image.height(), image.content().length, Sha256.hex(image.content()));
    }

    /** Removes the object of an image that is not, or no longer, referenced by an episode. */
    void discard(String podcastSlug, UUID episodeId, ImageFormat format) {
        storage.discard(MediaKeys.episodeImage(podcastSlug, episodeId, format.fileExtension()));
    }
}

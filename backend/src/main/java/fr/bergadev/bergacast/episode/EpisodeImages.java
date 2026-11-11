package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.config.BergacastProperties;
import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.image.ImageResizer;
import fr.bergadev.bergacast.image.InvalidImageException;
import fr.bergadev.bergacast.image.ProcessedImage;
import fr.bergadev.bergacast.storage.MediaCacheControl;
import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.storage.ObjectStorage;
import fr.bergadev.bergacast.web.ContentTooLargeException;
import fr.bergadev.bergacast.web.ImageUploads;
import fr.bergadev.bergacast.web.Sha256;
import fr.bergadev.bergacast.web.UnprocessableContentException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
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

    private final ImageResizer resizer;
    private final ObjectStorage storage;
    private final DataSize maxImageSize;

    EpisodeImages(ImageResizer resizer, ObjectStorage storage, BergacastProperties properties) {
        this.resizer = resizer;
        this.storage = storage;
        this.maxImageSize = properties.maxImageUploadSize();
    }

    /**
     * Reads and checks the image part of a publication.
     *
     * @throws UnsupportedMediaTypeException when the part is neither {@code image/jpeg} nor {@code image/png}
     * @throws ContentTooLargeException when the part is larger than the image upload limit
     * @throws UnprocessableContentException when the content is not a valid image of the declared type
     */
    ProcessedImage fitPart(MultipartFile part) {
        ImageFormat format = ImageUploads.declaredFormat(part.getContentType(), "The " + IMAGE_PART + " part");
        if (part.getSize() > maxImageSize.toBytes()) {
            throw new ContentTooLargeException(
                    "The " + IMAGE_PART + " part exceeds the maximum size of " + maxImageSize);
        }
        try {
            return fit(part.getBytes(), format, IMAGE_PART);
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
                MediaCacheControl.COVER_IMAGE);
        return new EpisodeImage(
                format.mimeType(), image.width(), image.height(), image.content().length, Sha256.hex(image.content()));
    }

    /** Removes the object of an image that is not, or no longer, referenced by an episode. */
    void discard(String podcastSlug, UUID episodeId, ImageFormat format) {
        storage.discard(MediaKeys.episodeImage(podcastSlug, episodeId, format.fileExtension()));
    }
}

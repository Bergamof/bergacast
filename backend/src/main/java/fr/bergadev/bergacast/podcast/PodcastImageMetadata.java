package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.web.EntityTags;
import java.util.UUID;

/**
 * Everything about the cover image of a podcast but its content.
 *
 * @param podcastId podcast the image belongs to
 * @param mimeType media type
 * @param width width in pixels
 * @param height height in pixels
 * @param sizeBytes size of the content in bytes
 * @param sha256 lowercase hexadecimal SHA-256 of the content
 */
public record PodcastImageMetadata(
        UUID podcastId, String mimeType, int width, int height, long sizeBytes, String sha256) {

    public ImageFormat format() {
        return ImageFormat.fromMimeType(mimeType)
                .orElseThrow(() -> new IllegalStateException("Unsupported stored image type " + mimeType));
    }

    /** Strong entity tag of the image, which identifies its content. */
    public String entityTag() {
        return EntityTags.strong(sha256);
    }
}

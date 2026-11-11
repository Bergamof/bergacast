package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.web.EntityTags;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Cover image of an episode, stored with the episode: all its columns are null when the episode has no image. The
 * content is an object of the storage (see {@link fr.bergadev.bergacast.storage.MediaKeys#episodeImage}).
 *
 * @param mimeType media type
 * @param width width in pixels
 * @param height height in pixels
 * @param sizeBytes size of the content in bytes
 * @param sha256 lowercase hexadecimal SHA-256 of the content
 */
@Embeddable
public record EpisodeImage(
        @Column(name = "image_mime_type") String mimeType,
        @Column(name = "image_width") int width,
        @Column(name = "image_height") int height,
        @Column(name = "image_size_bytes") long sizeBytes,
        @Column(name = "image_sha256") String sha256) {

    public ImageFormat format() {
        return ImageFormat.fromMimeType(mimeType)
                .orElseThrow(() -> new IllegalStateException("Unsupported stored image type " + mimeType));
    }

    /** Strong entity tag of the image, which identifies its content. */
    public String entityTag() {
        return EntityTags.strong(sha256);
    }
}

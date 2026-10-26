package fr.bergadev.bergacast.podcast;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import org.springframework.hateoas.server.core.Relation;

/**
 * Representation of the cover image uploaded for a podcast.
 *
 * @param mimeType media type
 * @param width width in pixels
 * @param height height in pixels
 * @param sizeBytes size in bytes
 * @param sha256 lowercase hexadecimal SHA-256 of the content
 * @param url absolute public URL of the content
 */
@Relation(itemRelation = "image")
@Schema(description = "Cover image uploaded for a podcast, downscaled to at most 3000 x 3000 pixels")
public record PodcastImageResponse(
        @Schema(
                description = "Media type of the image",
                example = "image/jpeg",
                allowableValues = {"image/jpeg", "image/png"})
        String mimeType,

        @Schema(description = "Width in pixels", example = "3000", minimum = "1")
        int width,

        @Schema(description = "Height in pixels", example = "3000", minimum = "1")
        int height,

        @Schema(description = "Size of the content in bytes", example = "1048576", minimum = "1")
        long sizeBytes,

        @Schema(
                description = "Lowercase hexadecimal SHA-256 of the content, also its entity tag",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                pattern = "^[0-9a-f]{64}$")
        String sha256,

        @Schema(
                description = "Absolute public URL of the content, used in the RSS feed (itunes:image)",
                example = "https://podcasts.example.com/my-podcast/image.jpg")
        URI url) {

    static PodcastImageResponse of(PodcastImageMetadata image, URI url) {
        return new PodcastImageResponse(
                image.mimeType(), image.width(), image.height(), image.sizeBytes(), image.sha256(), url);
    }
}

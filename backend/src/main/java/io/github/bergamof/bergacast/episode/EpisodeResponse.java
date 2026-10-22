package io.github.bergamof.bergacast.episode;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.server.core.Relation;

/**
 * Representation of an episode.
 *
 * @param id identifier
 * @param guid identifier of the item in the feed
 * @param title title
 * @param description description
 * @param publicationDate publication date
 * @param addedAt upload time
 * @param durationSeconds duration of the audio
 * @param season season number
 * @param episodeNumber episode number
 * @param episodeType episode type
 * @param explicit whether the episode contains explicit content
 * @param audio audio file
 */
@Relation(itemRelation = "episode", collectionRelation = "episodes")
public record EpisodeResponse(
        UUID id,
        String guid,
        String title,
        @Nullable String description,
        Instant publicationDate,
        Instant addedAt,
        @Nullable Integer durationSeconds,
        @Nullable Integer season,
        @Nullable Integer episodeNumber,
        EpisodeType episodeType,
        @Nullable Boolean explicit,
        Audio audio) {

    /**
     * Audio file of an episode.
     *
     * @param mimeType media type
     * @param sizeBytes size in bytes
     * @param sha256 lowercase hexadecimal SHA-256 of the content
     * @param url absolute public URL
     */
    public record Audio(String mimeType, long sizeBytes, String sha256, URI url) {}
}

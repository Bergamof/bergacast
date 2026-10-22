package io.github.bergamof.bergacast.feed;

import io.github.bergamof.bergacast.episode.EpisodeType;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An episode as rendered in a feed.
 *
 * @param id episode identifier, last tie-breaker of the sort
 * @param guid item guid
 * @param title item title
 * @param description item description
 * @param publicationDate publication date ({@code pubDate})
 * @param addedAt upload time
 * @param durationSeconds {@code itunes:duration}
 * @param season {@code itunes:season}
 * @param episodeNumber {@code itunes:episode}
 * @param episodeType {@code itunes:episodeType}
 * @param explicit {@code itunes:explicit}, omitted when {@code null}
 * @param enclosure audio enclosure
 */
public record FeedItem(
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
        Enclosure enclosure) {

    /**
     * Audio file of an item.
     *
     * @param url absolute public URL
     * @param lengthBytes size in bytes
     * @param mimeType media type
     */
    public record Enclosure(URI url, long lengthBytes, String mimeType) {}
}

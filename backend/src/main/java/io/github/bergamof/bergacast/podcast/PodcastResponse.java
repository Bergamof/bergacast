package io.github.bergamof.bergacast.podcast;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.server.core.Relation;

/**
 * Representation of a podcast.
 *
 * @param id identifier
 * @param slug URL identifier
 * @param title channel title
 * @param description channel description
 * @param author {@code itunes:author}
 * @param ownerName name in {@code itunes:owner}
 * @param ownerEmail email in {@code itunes:owner}
 * @param language BCP 47 language tag
 * @param category Apple Podcasts category
 * @param explicit whether the podcast contains explicit content
 * @param imageUrl URL of the cover art
 * @param websiteUrl URL of the website
 * @param feed feed settings
 * @param feedUrl absolute public URL of the RSS feed
 * @param publishUrl absolute URL where automated tools upload episodes
 * @param episodeCount number of episodes, including unpublished ones
 * @param createdAt creation time
 * @param updatedAt last modification time
 */
@Relation(itemRelation = "podcast", collectionRelation = "podcasts")
public record PodcastResponse(
        UUID id,
        String slug,
        String title,
        String description,
        @Nullable String author,
        @Nullable String ownerName,
        @Nullable String ownerEmail,
        String language,
        @Nullable String category,
        boolean explicit,
        @Nullable String imageUrl,
        @Nullable String websiteUrl,
        FeedSettings feed,
        URI feedUrl,
        URI publishUrl,
        long episodeCount,
        Instant createdAt,
        Instant updatedAt) {}

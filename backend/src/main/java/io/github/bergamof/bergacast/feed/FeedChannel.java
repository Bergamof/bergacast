package io.github.bergamof.bergacast.feed;

import java.net.URI;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Channel-level information of a feed.
 *
 * @param title channel title
 * @param link website of the podcast
 * @param description channel description
 * @param language BCP 47 language tag
 * @param lastBuildDate last time the content of the channel changed
 * @param author {@code itunes:author}
 * @param ownerName name in {@code itunes:owner}
 * @param ownerEmail email in {@code itunes:owner}
 * @param imageUrl {@code itunes:image}
 * @param category {@code itunes:category}
 * @param explicit {@code itunes:explicit}
 * @param selfUrl public URL of the feed itself ({@code atom:link rel="self"})
 */
public record FeedChannel(
        String title,
        URI link,
        String description,
        String language,
        Instant lastBuildDate,
        @Nullable String author,
        @Nullable String ownerName,
        @Nullable String ownerEmail,
        @Nullable String imageUrl,
        @Nullable String category,
        boolean explicit,
        URI selfUrl) {}

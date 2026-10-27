package fr.bergadev.bergacast.feed;

import java.net.URI;
import java.time.Instant;

/**
 * A feed written to the object storage.
 *
 * @param feedUrl public URL of the feed
 * @param publishedAt when it was written
 * @param episodeCount number of episodes it lists
 */
public record FeedPublication(URI feedUrl, Instant publishedAt, int episodeCount) {}

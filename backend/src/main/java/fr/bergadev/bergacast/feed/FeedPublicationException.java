package fr.bergadev.bergacast.feed;

/**
 * The feed of a podcast could not be written to the object storage. When it follows a change, the change itself is
 * committed: only the feed is behind, until it is written again.
 */
public class FeedPublicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String podcastSlug;

    FeedPublicationException(String podcastSlug, Throwable cause) {
        super("Cannot write the feed of the podcast '" + podcastSlug + "'", cause);
        this.podcastSlug = podcastSlug;
    }

    public String getPodcastSlug() {
        return podcastSlug;
    }
}

package fr.bergadev.bergacast.feed;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;

/** Representation of a feed written to the object storage. */
@Schema(description = "A publication of the RSS feed of a podcast to the object storage.")
public record FeedPublicationResponse(
        @Schema(
                description = "Public URL of the written feed, to add to podcast apps.",
                example = "https://media.example.com/my-podcast.xml")
        URI feedUrl,

        @Schema(description = "Time the feed was written (UTC).", example = "2026-10-27T08:15:00Z")
        Instant publishedAt,

        @Schema(description = "Number of episodes the feed lists.", example = "12")
        int episodeCount) {

    static FeedPublicationResponse of(FeedPublication publication) {
        return new FeedPublicationResponse(
                publication.feedUrl(), publication.publishedAt(), publication.episodeCount());
    }
}

package fr.bergadev.bergacast.podcast;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.server.core.Relation;

/** Representation of a podcast. */
@Relation(itemRelation = "podcast", collectionRelation = "podcasts")
@Schema(description = "A podcast, with its feed settings and public URLs.")
public record PodcastResponse(
        @Schema(description = "Identifier of the podcast.", example = "5d0a3c1e-8f4b-4b7a-9c2d-1e6f7a8b9c0d")
        UUID id,

        @Schema(description = "URL identifier of the podcast, immutable.", example = "my-podcast")
        String slug,

        @Schema(description = "Title of the podcast (channel `title`).", example = "My Podcast")
        String title,

        @Schema(
                description = "Description of the podcast (channel `description`).",
                example = "Conversations about self-hosting, open source and the open web.")
        String description,

        @Schema(description = "Author shown by podcast apps (`itunes:author`).", example = "Jane Doe", nullable = true)
        @Nullable
        String author,

        @Schema(description = "Name of the owner (`itunes:owner`).", example = "Jane Doe", nullable = true) @Nullable
        String ownerName,

        @Schema(
                description = "Contact email of the owner (`itunes:owner`).",
                example = "jane@example.com",
                nullable = true)
        @Nullable
        String ownerEmail,

        @Schema(description = "BCP 47 language tag of the content (channel `language`).", example = "en-US")
        String language,

        @Schema(description = "Apple Podcasts category (`itunes:category`).", example = "Technology", nullable = true)
        @Nullable
        String category,

        @Schema(description = "Whether the podcast contains explicit content (`itunes:explicit`).", example = "false")
        boolean explicit,

        @Schema(
                description = "URL of the cover art (`itunes:image`).",
                example = "https://example.com/cover.jpg",
                nullable = true)
        @Nullable
        String imageUrl,

        @Schema(
                description = "URL of the podcast website (channel `link`).",
                example = "https://example.com",
                nullable = true)
        @Nullable
        String websiteUrl,

        @Schema(description = "Rules selecting and ordering the episodes of the feed.")
        FeedSettings feed,

        @Schema(
                description = "Absolute public URL of the RSS feed, to submit to podcast directories.",
                example = "https://podcasts.example.com/my-podcast.xml")
        URI feedUrl,

        @Schema(
                description = "Absolute URL where automated tools publish episodes (POST multipart, with an "
                        + "API token of this podcast).",
                example = "https://podcasts.example.com/api/v1/my-podcast/episodes")
        URI publishUrl,

        @Schema(description = "Number of episodes, including those not published yet.", example = "42")
        long episodeCount,

        @Schema(description = "Creation time (UTC).", example = "2026-10-22T09:30:00Z")
        Instant createdAt,

        @Schema(description = "Last modification time (UTC).", example = "2026-10-25T14:12:03Z")
        Instant updatedAt) {}

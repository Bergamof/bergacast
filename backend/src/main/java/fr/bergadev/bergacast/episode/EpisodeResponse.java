package fr.bergadev.bergacast.episode;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.server.core.Relation;

/** Representation of an episode. */
@Relation(itemRelation = "episode", collectionRelation = "episodes")
@Schema(description = "An episode of a podcast, with its audio file.")
public record EpisodeResponse(
        @Schema(description = "Identifier of the episode in the API.", example = "0b8e5f3c-6d2a-4f4e-9a57-2f0c1d7e8a90")
        UUID id,

        @Schema(
                description = "Identifier of the item in the feed (RSS `guid`), unique within the podcast.",
                example = "my-podcast-042")
        String guid,

        @Schema(description = "Title of the episode.", example = "Episode 42: Self-hosting a podcast")
        String title,

        @Schema(
                description = "Show notes, published as the description of the feed item.",
                example = "We set up a podcast server at home and discuss RSS feeds.",
                nullable = true)
        @Nullable
        String description,

        @Schema(
                description = "Publication date and time (UTC). The episode is in the feed only from this "
                        + "moment, unless the podcast feed includes future episodes.",
                example = "2026-11-02T05:00:00Z")
        Instant publicationDate,

        @Schema(description = "Time the episode was uploaded (UTC).", example = "2026-10-25T14:12:03Z")
        Instant addedAt,

        @Schema(
                description = "Duration of the audio in seconds (`itunes:duration`).",
                example = "2712",
                nullable = true)
        @Nullable
        Integer durationSeconds,

        @Schema(description = "Season number (`itunes:season`).", example = "3", nullable = true) @Nullable
        Integer season,

        @Schema(description = "Episode number (`itunes:episode`).", example = "42", nullable = true) @Nullable
        Integer episodeNumber,

        @Schema(description = "Type of the episode (`itunes:episodeType`).", example = "FULL")
        EpisodeType episodeType,

        @Schema(
                description = "Whether the episode contains explicit content (`itunes:explicit`); null when "
                        + "the podcast setting applies.",
                example = "false",
                nullable = true)
        @Nullable
        Boolean explicit,

        @Schema(description = "Audio file of the episode.") Audio audio) {

    /** Audio file of an episode. */
    @Schema(description = "Audio file of an episode.")
    public record Audio(
            @Schema(description = "Media type of the audio, as declared on upload.", example = "audio/mpeg")
            String mimeType,

            @Schema(description = "Size of the file in bytes.", example = "43393024")
            long sizeBytes,

            @Schema(
                    description = "Lowercase hexadecimal SHA-256 of the content, also its ETag on download.",
                    example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
            String sha256,

            @Schema(
                    description = "Absolute public URL of the file, used as the enclosure of the feed item. "
                            + "Its extension is derived from the media type.",
                    example = "https://podcasts.example.com/my-podcast/episodes/"
                            + "0b8e5f3c-6d2a-4f4e-9a57-2f0c1d7e8a90/audio.mp3")
            URI url) {}
}

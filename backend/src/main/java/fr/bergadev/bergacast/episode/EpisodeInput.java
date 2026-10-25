package fr.bergadev.bergacast.episode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Episode metadata submitted on upload (the {@code metadata} part) and target of a JSON Merge Patch. */
@Schema(
        description = "Metadata of an episode: the `metadata` part of a publication, or the target of a JSON Merge "
                + "Patch. Absent optional values take their default on publication.")
public record EpisodeInput(
        @Schema(description = "Title of the episode.", example = "Episode 42: Self-hosting a podcast")
        @NotBlank
        @Size(max = EpisodeInput.MAX_TEXT_LENGTH)
        String title,

        @Schema(
                description = "Show notes, published as the description of the feed item. Null for none.",
                example = "We set up a podcast server at home and discuss RSS feeds.",
                nullable = true)
        @Nullable
        String description,

        @Schema(
                description = "Publication date and time (ISO 8601 with offset). The episode appears in the "
                        + "feed from this moment: a date in the future schedules it, unless the podcast feed "
                        + "includes future episodes. Defaults to the upload time; cannot be null in a PATCH.",
                example = "2026-11-02T06:00:00+01:00")
        @NotNull(groups = EpisodeInput.Update.class)
        @Nullable
        OffsetDateTime publicationDate,

        @Schema(
                description = "Identifier of the item in the feed (RSS `guid`), unique within the podcast. "
                        + "Podcast apps use it to recognize an episode: never change it once published. "
                        + "Defaults to a random UUID; cannot be null in a PATCH.",
                example = "my-podcast-042")
        @NotNull(groups = EpisodeInput.Update.class)
        @Size(min = 1, max = EpisodeInput.MAX_TEXT_LENGTH)
        @Nullable
        String guid,

        @Schema(
                description = "Duration of the audio in seconds (`itunes:duration`), as declared by the "
                        + "publisher. Null if unknown.",
                example = "2712",
                nullable = true)
        @PositiveOrZero
        @Nullable
        Integer durationSeconds,

        @Schema(description = "Season number (`itunes:season`). Null for none.", example = "3", nullable = true)
        @Positive
        @Nullable
        Integer season,

        @Schema(description = "Episode number (`itunes:episode`). Null for none.", example = "42", nullable = true)
        @Positive
        @Nullable
        Integer episodeNumber,

        @Schema(
                description = "Type of the episode (`itunes:episodeType`): FULL (regular content), TRAILER "
                        + "(preview) or BONUS (extra content). Defaults to FULL; cannot be null in a PATCH.",
                defaultValue = "FULL",
                example = "FULL")
        @NotNull(groups = EpisodeInput.Update.class)
        @Nullable
        EpisodeType episodeType,

        @Schema(
                description = "Whether the episode contains explicit content (`itunes:explicit`). Null "
                        + "leaves it out of the item: podcast apps then use the podcast setting.",
                example = "false",
                nullable = true)
        @Nullable
        Boolean explicit) {

    static final int MAX_TEXT_LENGTH = 255;

    /** Validation group of updates, where defaulted values must stay present. */
    interface Update {}

    static EpisodeInput from(EpisodeMetadata metadata) {
        return new EpisodeInput(
                metadata.title(),
                metadata.description(),
                metadata.publicationDate().atOffset(ZoneOffset.UTC),
                metadata.guid(),
                metadata.durationSeconds(),
                metadata.season(),
                metadata.episodeNumber(),
                metadata.episodeType(),
                metadata.explicit());
    }

    /** Converts to metadata, filling missing values with their defaults. */
    EpisodeMetadata toMetadata(Instant now) {
        return new EpisodeMetadata(
                guid == null ? UUID.randomUUID().toString() : guid,
                title,
                description,
                publicationDate == null ? now : publicationDate.toInstant(),
                durationSeconds,
                season,
                episodeNumber,
                Objects.requireNonNullElse(episodeType, EpisodeType.FULL),
                explicit);
    }
}

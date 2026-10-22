package fr.bergadev.bergacast.episode;

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

/**
 * Episode metadata submitted on upload (the {@code metadata} part) and target of a JSON Merge Patch.
 *
 * @param title title
 * @param description description (show notes)
 * @param publicationDate publication date (ISO 8601 with offset), defaults to the upload time
 * @param guid identifier of the item in the feed, unique within the podcast, defaults to a random UUID
 * @param durationSeconds duration of the audio
 * @param season season number
 * @param episodeNumber episode number
 * @param episodeType episode type, defaults to {@code FULL}
 * @param explicit whether the episode contains explicit content
 */
public record EpisodeInput(
        @NotBlank @Size(max = EpisodeInput.MAX_TEXT_LENGTH) String title,
        @Nullable String description,

        @NotNull(groups = EpisodeInput.Update.class) @Nullable
        OffsetDateTime publicationDate,

        @NotNull(groups = EpisodeInput.Update.class) @Size(min = 1, max = EpisodeInput.MAX_TEXT_LENGTH) @Nullable
        String guid,

        @PositiveOrZero @Nullable Integer durationSeconds,
        @Positive @Nullable Integer season,
        @Positive @Nullable Integer episodeNumber,

        @NotNull(groups = EpisodeInput.Update.class) @Nullable
        EpisodeType episodeType,

        @Nullable Boolean explicit) {

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

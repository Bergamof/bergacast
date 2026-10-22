package fr.bergadev.bergacast.episode;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Editable metadata of an episode.
 *
 * @param guid identifier of the item in the feed, unique within the podcast
 * @param title title
 * @param description description (show notes)
 * @param publicationDate publication date, may be in the future
 * @param durationSeconds duration of the audio
 * @param season season number
 * @param episodeNumber episode number
 * @param episodeType episode type
 * @param explicit whether the episode contains explicit content, {@code null} to omit the information
 */
public record EpisodeMetadata(
        String guid,
        String title,
        @Nullable String description,
        Instant publicationDate,
        @Nullable Integer durationSeconds,
        @Nullable Integer season,
        @Nullable Integer episodeNumber,
        EpisodeType episodeType,
        @Nullable Boolean explicit) {}

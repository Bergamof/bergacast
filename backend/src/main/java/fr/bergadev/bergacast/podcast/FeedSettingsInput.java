package fr.bergadev.bergacast.podcast;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Feed settings as submitted by a client; missing values fall back to {@link FeedSettings#defaults()}. */
@Schema(description = FeedSettings.DESCRIPTION + " Absent or null values take their default.")
public record FeedSettingsInput(
        @Schema(description = FeedSettings.MAX_EPISODES, example = "50", nullable = true) @Positive @Nullable
        Integer maxEpisodes,

        @Schema(
                description = FeedSettings.EVICT_OLDEST_BY,
                defaultValue = "PUBLICATION_DATE",
                example = "PUBLICATION_DATE")
        @Nullable
        EpisodeOrdering evictOldestBy,

        @Schema(description = FeedSettings.SORT_BY, defaultValue = "PUBLICATION_DATE", example = "PUBLICATION_DATE")
        @Nullable
        EpisodeOrdering sortBy,

        @Schema(description = FeedSettings.SORT_DIRECTION, defaultValue = "DESC", example = "DESC") @Nullable
        SortDirection sortDirection,

        @Schema(
                description = FeedSettings.INCLUDE_FUTURE_EPISODES,
                defaultValue = "false",
                example = "false",
                nullable = true)
        @Nullable
        Boolean includeFutureEpisodes) {

    static FeedSettingsInput from(FeedSettings settings) {
        return new FeedSettingsInput(
                settings.maxEpisodes(),
                settings.evictOldestBy(),
                settings.sortBy(),
                settings.sortDirection(),
                settings.includeFutureEpisodes());
    }

    FeedSettings toFeedSettings() {
        FeedSettings defaults = FeedSettings.defaults();
        return new FeedSettings(
                maxEpisodes,
                Objects.requireNonNullElse(evictOldestBy, defaults.evictOldestBy()),
                Objects.requireNonNullElse(sortBy, defaults.sortBy()),
                Objects.requireNonNullElse(sortDirection, defaults.sortDirection()),
                Objects.requireNonNullElse(includeFutureEpisodes, defaults.includeFutureEpisodes()));
    }
}

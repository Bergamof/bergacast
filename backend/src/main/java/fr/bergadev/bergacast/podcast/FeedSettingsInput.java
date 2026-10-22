package fr.bergadev.bergacast.podcast;

import jakarta.validation.constraints.Positive;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Feed settings as submitted by a client; missing values fall back to {@link FeedSettings#defaults()}.
 *
 * @param maxEpisodes maximum number of episodes in the feed, {@code null} for all of them
 * @param evictOldestBy date used to drop the oldest episodes when {@code maxEpisodes} is set
 * @param sortBy date used to sort the feed items
 * @param sortDirection direction of the sort
 * @param includeFutureEpisodes whether episodes with a future publication date are listed
 */
public record FeedSettingsInput(
        @Positive @Nullable Integer maxEpisodes,
        @Nullable EpisodeOrdering evictOldestBy,
        @Nullable EpisodeOrdering sortBy,
        @Nullable SortDirection sortDirection,
        @Nullable Boolean includeFutureEpisodes) {

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

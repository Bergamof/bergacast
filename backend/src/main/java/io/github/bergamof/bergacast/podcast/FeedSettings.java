package io.github.bergamof.bergacast.podcast;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.jspecify.annotations.Nullable;

/**
 * Rules that select and order the episodes of a podcast feed.
 *
 * @param maxEpisodes maximum number of episodes in the feed, {@code null} for all of them
 * @param evictOldestBy date used to drop the oldest episodes when {@code maxEpisodes} is set
 * @param sortBy date used to sort the feed items
 * @param sortDirection direction of the sort
 * @param includeFutureEpisodes whether episodes with a future publication date are listed
 */
@Embeddable
public record FeedSettings(
        @Column(name = "max_episodes") @Nullable Integer maxEpisodes,

        @Column(name = "evict_oldest_by", nullable = false) @Enumerated(EnumType.STRING)
        EpisodeOrdering evictOldestBy,

        @Column(name = "sort_by", nullable = false) @Enumerated(EnumType.STRING)
        EpisodeOrdering sortBy,

        @Column(name = "sort_direction", nullable = false) @Enumerated(EnumType.STRING)
        SortDirection sortDirection,

        @Column(name = "include_future_episodes", nullable = false)
        boolean includeFutureEpisodes) {

    public static final EpisodeOrdering DEFAULT_ORDERING = EpisodeOrdering.PUBLICATION_DATE;
    public static final SortDirection DEFAULT_SORT_DIRECTION = SortDirection.DESC;

    /** All published episodes, newest publication first. */
    public static FeedSettings defaults() {
        return new FeedSettings(null, DEFAULT_ORDERING, DEFAULT_ORDERING, DEFAULT_SORT_DIRECTION, false);
    }
}

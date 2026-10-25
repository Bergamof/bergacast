package fr.bergadev.bergacast.podcast;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.jspecify.annotations.Nullable;

/** Rules that select and order the episodes of a podcast feed. */
@Embeddable
@Schema(description = FeedSettings.DESCRIPTION)
public record FeedSettings(
        @Schema(description = FeedSettings.MAX_EPISODES, example = "50", nullable = true)
        @Column(name = "max_episodes")
        @Nullable
        Integer maxEpisodes,

        @Schema(description = FeedSettings.EVICT_OLDEST_BY, example = "PUBLICATION_DATE")
        @Column(name = "evict_oldest_by", nullable = false)
        @Enumerated(EnumType.STRING)
        EpisodeOrdering evictOldestBy,

        @Schema(description = FeedSettings.SORT_BY, example = "PUBLICATION_DATE")
        @Column(name = "sort_by", nullable = false)
        @Enumerated(EnumType.STRING)
        EpisodeOrdering sortBy,

        @Schema(description = FeedSettings.SORT_DIRECTION, example = "DESC")
        @Column(name = "sort_direction", nullable = false)
        @Enumerated(EnumType.STRING)
        SortDirection sortDirection,

        @Schema(description = FeedSettings.INCLUDE_FUTURE_EPISODES, example = "false")
        @Column(name = "include_future_episodes", nullable = false)
        boolean includeFutureEpisodes) {

    public static final EpisodeOrdering DEFAULT_ORDERING = EpisodeOrdering.PUBLICATION_DATE;
    public static final SortDirection DEFAULT_SORT_DIRECTION = SortDirection.DESC;

    // OpenAPI descriptions, shared with FeedSettingsInput.
    static final String DESCRIPTION = "Rules selecting and ordering the episodes of a podcast feed.";
    static final String MAX_EPISODES = "Maximum number of episodes in the feed: the oldest ones (according to "
            + "evictOldestBy) are left out. Null for no limit.";
    static final String EVICT_OLDEST_BY = "Date deciding which episodes are the oldest when maxEpisodes is set: "
            + "PUBLICATION_DATE (chosen by the publisher) or ADDED_DATE (upload time).";
    static final String SORT_BY = "Date by which the feed items are sorted: PUBLICATION_DATE or ADDED_DATE (ties are "
            + "broken by the other date, then by id).";
    static final String SORT_DIRECTION =
            "Direction of the sort of the feed items: DESC (newest first) or ASC (oldest first, e.g. for a series).";
    static final String INCLUDE_FUTURE_EPISODES = "Whether episodes whose publicationDate is in the future are "
            + "already listed in the feed; otherwise each one appears at its publication date.";

    /** All published episodes, newest publication first. */
    public static FeedSettings defaults() {
        return new FeedSettings(null, DEFAULT_ORDERING, DEFAULT_ORDERING, DEFAULT_SORT_DIRECTION, false);
    }
}

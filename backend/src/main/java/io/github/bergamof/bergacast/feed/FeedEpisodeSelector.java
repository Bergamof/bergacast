package io.github.bergamof.bergacast.feed;

import io.github.bergamof.bergacast.podcast.EpisodeOrdering;
import io.github.bergamof.bergacast.podcast.FeedSettings;
import io.github.bergamof.bergacast.podcast.SortDirection;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Selects and orders the items of a feed according to the podcast {@link FeedSettings}.
 *
 * <ol>
 *   <li>unless future episodes are included, episodes published after now are dropped;
 *   <li>when a maximum is set, only the most recent episodes by {@code evictOldestBy} are kept;
 *   <li>the remaining episodes are sorted by {@code sortBy} and {@code sortDirection}, ties being broken by the
 *       other date, then by identifier.
 * </ol>
 */
@Component
public class FeedEpisodeSelector {

    private final Clock clock;

    public FeedEpisodeSelector(Clock clock) {
        this.clock = clock;
    }

    public List<FeedItem> select(List<FeedItem> episodes, FeedSettings settings) {
        Instant now = clock.instant();
        List<FeedItem> visible = settings.includeFutureEpisodes()
                ? episodes
                : episodes.stream()
                        .filter(episode -> !episode.publicationDate().isAfter(now))
                        .toList();
        List<FeedItem> kept = settings.maxEpisodes() == null
                ? visible
                : visible.stream()
                        .sorted(chronological(settings.evictOldestBy()).reversed())
                        .limit(settings.maxEpisodes())
                        .toList();
        Comparator<FeedItem> order = chronological(settings.sortBy());
        return kept.stream()
                .sorted(settings.sortDirection() == SortDirection.ASC ? order : order.reversed())
                .toList();
    }

    /** Oldest first according to {@code ordering}, then to the other date, then to the identifier. */
    static Comparator<FeedItem> chronological(EpisodeOrdering ordering) {
        Comparator<FeedItem> byPublicationDate = Comparator.comparing(FeedItem::publicationDate);
        Comparator<FeedItem> byAddedDate = Comparator.comparing(FeedItem::addedAt);
        Comparator<FeedItem> dates = switch (ordering) {
            case PUBLICATION_DATE -> byPublicationDate.thenComparing(byAddedDate);
            case ADDED_DATE -> byAddedDate.thenComparing(byPublicationDate);
        };
        return dates.thenComparing(FeedItem::id);
    }
}

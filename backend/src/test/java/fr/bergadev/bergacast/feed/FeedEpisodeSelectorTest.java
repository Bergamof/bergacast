package fr.bergadev.bergacast.feed;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.episode.EpisodeType;
import fr.bergadev.bergacast.podcast.EpisodeOrdering;
import fr.bergadev.bergacast.podcast.FeedSettings;
import fr.bergadev.bergacast.podcast.SortDirection;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FeedEpisodeSelectorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final FeedEpisodeSelector SELECTOR = new FeedEpisodeSelector(Clock.fixed(NOW, ZoneOffset.UTC));

    /*
     * Episodes whose publication and upload orders differ, so that each ordering gives a distinct result:
     *   by publication date: A (-4d) < B (-3d) < C (-2d) < D (-1d) < FUTURE (+1d)
     *   by added date:       C (-10d) < A (-9d) < D (-8d) < B (-7d) < FUTURE (-6d)
     */
    private static final FeedItem A = item("A", days(-4), days(-9));
    private static final FeedItem B = item("B", days(-3), days(-7));
    private static final FeedItem C = item("C", days(-2), days(-10));
    private static final FeedItem D = item("D", days(-1), days(-8));
    private static final FeedItem FUTURE = item("FUTURE", days(1), days(-6));
    private static final List<FeedItem> EPISODES = List.of(B, FUTURE, D, A, C);

    static Stream<Arguments> combinations() {
        EpisodeOrdering pub = EpisodeOrdering.PUBLICATION_DATE;
        EpisodeOrdering add = EpisodeOrdering.ADDED_DATE;
        SortDirection asc = SortDirection.ASC;
        SortDirection desc = SortDirection.DESC;
        return Stream.of(
                // no limit: evictOldestBy is irrelevant
                Arguments.of(null, pub, pub, desc, false, List.of(D, C, B, A)),
                Arguments.of(null, add, pub, asc, false, List.of(A, B, C, D)),
                Arguments.of(null, pub, add, desc, false, List.of(B, D, A, C)),
                Arguments.of(null, pub, add, asc, false, List.of(C, A, D, B)),
                Arguments.of(null, pub, pub, desc, true, List.of(FUTURE, D, C, B, A)),
                Arguments.of(null, pub, add, asc, true, List.of(C, A, D, B, FUTURE)),
                // limit of 2, evicting the oldest by publication date keeps C and D
                Arguments.of(2, pub, pub, desc, false, List.of(D, C)),
                Arguments.of(2, pub, pub, asc, false, List.of(C, D)),
                Arguments.of(2, pub, add, desc, false, List.of(D, C)),
                Arguments.of(2, pub, add, asc, false, List.of(C, D)),
                // limit of 2, evicting the oldest by added date keeps D and B
                Arguments.of(2, add, pub, desc, false, List.of(D, B)),
                Arguments.of(2, add, pub, asc, false, List.of(B, D)),
                Arguments.of(2, add, add, desc, false, List.of(B, D)),
                Arguments.of(2, add, add, asc, false, List.of(D, B)),
                // future episodes take part in the eviction when they are included
                Arguments.of(2, pub, pub, desc, true, List.of(FUTURE, D)),
                Arguments.of(2, add, add, asc, true, List.of(B, FUTURE)),
                // limit greater than the number of episodes
                Arguments.of(10, add, pub, desc, false, List.of(D, C, B, A)));
    }

    @ParameterizedTest(name = "max={0} evict={1} sort={2} {3} future={4}")
    @MethodSource("combinations")
    void selectsAndOrdersEpisodes(
            Integer maxEpisodes,
            EpisodeOrdering evictOldestBy,
            EpisodeOrdering sortBy,
            SortDirection direction,
            boolean includeFuture,
            List<FeedItem> expected) {
        FeedSettings settings = new FeedSettings(maxEpisodes, evictOldestBy, sortBy, direction, includeFuture);

        assertThat(SELECTOR.select(EPISODES, settings)).containsExactlyElementsOf(expected);
    }

    @Test
    void includesEpisodesPublishedExactlyNow() {
        FeedItem publishedNow = item("NOW", NOW, days(-1));

        assertThat(SELECTOR.select(List.of(publishedNow), FeedSettings.defaults()))
                .containsExactly(publishedNow);
    }

    @Test
    void breaksTiesWithTheOtherDateThenTheIdentifier() {
        Instant sameDay = days(-1);
        FeedItem addedLater = item("LATER", sameDay, days(-1));
        FeedItem addedEarlier = item("EARLIER", sameDay, days(-3));
        FeedItem sameDatesFirstId = new FeedItem(
                new UUID(0, 1),
                "X",
                "X",
                null,
                sameDay,
                days(-2),
                null,
                null,
                null,
                EpisodeType.FULL,
                null,
                enclosure());
        FeedItem sameDatesSecondId = new FeedItem(
                new UUID(0, 2),
                "Y",
                "Y",
                null,
                sameDay,
                days(-2),
                null,
                null,
                null,
                EpisodeType.FULL,
                null,
                enclosure());
        FeedSettings ascending = new FeedSettings(
                null, EpisodeOrdering.PUBLICATION_DATE, EpisodeOrdering.PUBLICATION_DATE, SortDirection.ASC, false);

        assertThat(SELECTOR.select(List.of(sameDatesSecondId, addedLater, sameDatesFirstId, addedEarlier), ascending))
                .extracting(FeedItem::guid)
                .containsExactly("EARLIER", "X", "Y", "LATER");
    }

    @Test
    void returnsNothingWithoutEpisodes() {
        assertThat(SELECTOR.select(List.of(), FeedSettings.defaults())).isEmpty();
    }

    private static Instant days(long offset) {
        return NOW.plus(Duration.ofDays(offset));
    }

    private static FeedItem item(String name, Instant publicationDate, Instant addedAt) {
        return new FeedItem(
                UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name,
                name,
                null,
                publicationDate,
                addedAt,
                null,
                null,
                null,
                EpisodeType.FULL,
                null,
                enclosure());
    }

    private static FeedItem.Enclosure enclosure() {
        return new FeedItem.Enclosure(URI.create("https://example.test/audio.mp3"), 1, "audio/mpeg");
    }
}

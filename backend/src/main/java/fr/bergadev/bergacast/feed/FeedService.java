package fr.bergadev.bergacast.feed;

import fr.bergadev.bergacast.audio.AudioFile;
import fr.bergadev.bergacast.episode.Episode;
import fr.bergadev.bergacast.episode.EpisodeMetadata;
import fr.bergadev.bergacast.episode.EpisodeService;
import fr.bergadev.bergacast.podcast.Podcast;
import fr.bergadev.bergacast.podcast.PodcastDetails;
import fr.bergadev.bergacast.podcast.PodcastService;
import fr.bergadev.bergacast.web.PublicUrls;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates the RSS feed of a podcast on each request, so that it always reflects the current data. */
@Service
@Transactional(readOnly = true)
public class FeedService {

    private final PodcastService podcastService;
    private final EpisodeService episodeService;
    private final FeedEpisodeSelector selector;
    private final RssFeedWriter writer;
    private final PublicUrls publicUrls;

    FeedService(
            PodcastService podcastService,
            EpisodeService episodeService,
            FeedEpisodeSelector selector,
            RssFeedWriter writer,
            PublicUrls publicUrls) {
        this.podcastService = podcastService;
        this.episodeService = episodeService;
        this.selector = selector;
        this.writer = writer;
        this.publicUrls = publicUrls;
    }

    /** Returns the UTF-8 encoded RSS document of the podcast identified by {@code slug}. */
    public byte[] render(String slug) {
        Podcast podcast = podcastService.getBySlug(slug);
        List<FeedItem> candidates = episodeService.listAll(podcast).stream()
                .map(episode -> toFeedItem(slug, episode))
                .toList();
        List<FeedItem> items = selector.select(candidates, podcast.getFeedSettings());
        return writer.write(toChannel(podcast, items), items);
    }

    private FeedChannel toChannel(Podcast podcast, List<FeedItem> items) {
        PodcastDetails details = podcast.getDetails();
        URI link = details.websiteUrl() == null ? publicUrls.base() : URI.create(details.websiteUrl());
        return new FeedChannel(
                details.title(),
                link,
                details.description(),
                details.language(),
                lastBuildDate(podcast, items),
                details.author(),
                details.ownerName(),
                details.ownerEmail(),
                details.imageUrl(),
                details.category(),
                details.explicit(),
                publicUrls.feed(podcast.getSlug()));
    }

    /**
     * The channel changes when the podcast is updated or an episode is added or becomes listed; a date derived from
     * the data (rather than the current time) keeps the feed, and thus its entity tag, stable between requests.
     */
    private static Instant lastBuildDate(Podcast podcast, List<FeedItem> items) {
        return Stream.concat(
                        Stream.of(podcast.getUpdatedAt()),
                        items.stream().flatMap(item -> Stream.of(item.addedAt(), item.publicationDate())))
                .max(Instant::compareTo)
                .orElseThrow();
    }

    private FeedItem toFeedItem(String slug, Episode episode) {
        EpisodeMetadata metadata = episode.metadata();
        AudioFile audio = episode.getAudioFile();
        return new FeedItem(
                episode.getId(),
                metadata.guid(),
                metadata.title(),
                metadata.description(),
                metadata.publicationDate(),
                episode.getAddedAt(),
                metadata.durationSeconds(),
                metadata.season(),
                metadata.episodeNumber(),
                metadata.episodeType(),
                metadata.explicit(),
                new FeedItem.Enclosure(
                        publicUrls.audio(slug, episode.getId(), audio.getFileExtension()),
                        audio.getSizeBytes(),
                        audio.getMimeType()));
    }
}

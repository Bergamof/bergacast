package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.web.ConflictException;
import fr.bergadev.bergacast.web.EntityTags;
import fr.bergadev.bergacast.web.JsonMergePatch;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import fr.bergadev.bergacast.web.UnprocessableContentException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Use cases of podcast administration. */
@Service
@Transactional
public class PodcastService {

    private static final String SLUG_FIELD = "slug";

    private final PodcastRepository podcastRepository;
    private final PodcastImageRepository imageRepository;
    private final JsonMergePatch jsonMergePatch;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PodcastService(
            PodcastRepository podcastRepository,
            PodcastImageRepository imageRepository,
            JsonMergePatch jsonMergePatch,
            ApplicationEventPublisher events,
            Clock clock) {
        this.podcastRepository = podcastRepository;
        this.imageRepository = imageRepository;
        this.jsonMergePatch = jsonMergePatch;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Returns the podcast identified by {@code slug}.
     *
     * @throws ResourceNotFoundException when there is no such podcast
     */
    @Transactional(readOnly = true)
    public Podcast getBySlug(String slug) {
        return podcastRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No podcast with slug '" + slug + "'"));
    }

    @Transactional(readOnly = true)
    public Page<PodcastOverview> list(Pageable pageable) {
        Page<Podcast> podcasts = podcastRepository.findAll(pageable);
        Map<UUID, Long> counts = countEpisodes(podcasts.getContent());
        Map<UUID, PodcastImageMetadata> images = imagesOf(podcasts.getContent());
        return podcasts.map(podcast ->
                new PodcastOverview(podcast, counts.getOrDefault(podcast.getId(), 0L), images.get(podcast.getId())));
    }

    @Transactional(readOnly = true)
    public PodcastOverview get(String slug) {
        return overviewOf(getBySlug(slug));
    }

    /** Creates a podcast; its (empty) feed is written once the podcast is committed. */
    public PodcastOverview create(PodcastInput input) {
        if (podcastRepository.existsBySlug(input.slug())) {
            throw new ConflictException("The slug '" + input.slug() + "' is already used by another podcast");
        }
        Podcast podcast = new Podcast(input.slug(), input.toDetails(), input.toFeedSettings(), clock.instant());
        PodcastOverview created = new PodcastOverview(podcastRepository.saveAndFlush(podcast), 0, null);
        events.publishEvent(new PodcastFeedChanged(input.slug()));
        return created;
    }

    /**
     * Applies a JSON Merge Patch to a podcast; its feed is written again once the change is committed.
     *
     * @param ifMatch optional {@code If-Match} header value, checked against the current entity tag
     */
    public PodcastOverview patch(String slug, JsonNode patch, @Nullable String ifMatch) {
        PodcastOverview current = get(slug);
        EntityTags.requireIfMatch(ifMatch, current.entityTag());
        Podcast podcast = current.podcast();
        PodcastInput patched = jsonMergePatch.apply(PodcastInput.from(podcast), patch, PodcastInput.class);
        if (!podcast.getSlug().equals(patched.slug())) {
            throw new UnprocessableContentException(SLUG_FIELD, "cannot be changed once the podcast is created");
        }
        podcast.update(patched.toDetails(), patched.toFeedSettings(), clock.instant());
        PodcastOverview updated =
                new PodcastOverview(podcastRepository.saveAndFlush(podcast), current.episodeCount(), current.image());
        events.publishEvent(new PodcastFeedChanged(slug));
        return updated;
    }

    /**
     * Deletes a podcast with its episodes, image and API tokens (database cascades and triggers); its objects (feed,
     * image, audio files) are removed from the storage once the deletion is committed.
     */
    public void delete(String slug) {
        podcastRepository.delete(getBySlug(slug));
        events.publishEvent(new PodcastDeleted(slug));
    }

    private PodcastOverview overviewOf(Podcast podcast) {
        return new PodcastOverview(
                podcast,
                countEpisodes(List.of(podcast)).getOrDefault(podcast.getId(), 0L),
                imageRepository.findMetadata(podcast.getId()).orElse(null));
    }

    /** Metadata only: the image contents of a page of podcasts are never loaded. */
    private Map<UUID, PodcastImageMetadata> imagesOf(List<Podcast> podcasts) {
        if (podcasts.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = podcasts.stream().map(Podcast::getId).toList();
        return imageRepository.findAllMetadata(ids).stream()
                .collect(Collectors.toMap(PodcastImageMetadata::podcastId, Function.identity()));
    }

    private Map<UUID, Long> countEpisodes(List<Podcast> podcasts) {
        if (podcasts.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = podcasts.stream().map(Podcast::getId).toList();
        return podcastRepository.countEpisodes(ids).stream()
                .collect(Collectors.toMap(EpisodeCount::podcastId, EpisodeCount::count));
    }
}

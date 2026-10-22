package io.github.bergamof.bergacast.podcast;

import io.github.bergamof.bergacast.web.ConflictException;
import io.github.bergamof.bergacast.web.EntityTags;
import io.github.bergamof.bergacast.web.JsonMergePatch;
import io.github.bergamof.bergacast.web.ResourceNotFoundException;
import io.github.bergamof.bergacast.web.UnprocessableContentException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
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
    private final JsonMergePatch jsonMergePatch;
    private final Clock clock;

    PodcastService(PodcastRepository podcastRepository, JsonMergePatch jsonMergePatch, Clock clock) {
        this.podcastRepository = podcastRepository;
        this.jsonMergePatch = jsonMergePatch;
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
    public Page<PodcastWithEpisodeCount> list(Pageable pageable) {
        Page<Podcast> podcasts = podcastRepository.findAll(pageable);
        Map<UUID, Long> counts = countEpisodes(podcasts.getContent());
        return podcasts.map(podcast -> new PodcastWithEpisodeCount(podcast, counts.getOrDefault(podcast.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public PodcastWithEpisodeCount get(String slug) {
        return withEpisodeCount(getBySlug(slug));
    }

    public PodcastWithEpisodeCount create(PodcastInput input) {
        if (podcastRepository.existsBySlug(input.slug())) {
            throw new ConflictException("The slug '" + input.slug() + "' is already used by another podcast");
        }
        Podcast podcast = new Podcast(input.slug(), input.toDetails(), input.toFeedSettings(), clock.instant());
        return new PodcastWithEpisodeCount(podcastRepository.saveAndFlush(podcast), 0);
    }

    /**
     * Applies a JSON Merge Patch to a podcast.
     *
     * @param ifMatch optional {@code If-Match} header value, checked against the current entity tag
     */
    public PodcastWithEpisodeCount patch(String slug, JsonNode patch, @Nullable String ifMatch) {
        PodcastWithEpisodeCount current = get(slug);
        EntityTags.requireIfMatch(ifMatch, current.entityTag());
        Podcast podcast = current.podcast();
        PodcastInput patched = jsonMergePatch.apply(PodcastInput.from(podcast), patch, PodcastInput.class);
        if (!podcast.getSlug().equals(patched.slug())) {
            throw new UnprocessableContentException(SLUG_FIELD, "cannot be changed once the podcast is created");
        }
        podcast.update(patched.toDetails(), patched.toFeedSettings(), clock.instant());
        return new PodcastWithEpisodeCount(podcastRepository.saveAndFlush(podcast), current.episodeCount());
    }

    /** Deletes a podcast with its episodes, audio content and API tokens (database cascades and triggers). */
    public void delete(String slug) {
        podcastRepository.delete(getBySlug(slug));
    }

    private PodcastWithEpisodeCount withEpisodeCount(Podcast podcast) {
        return new PodcastWithEpisodeCount(
                podcast, countEpisodes(List.of(podcast)).getOrDefault(podcast.getId(), 0L));
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

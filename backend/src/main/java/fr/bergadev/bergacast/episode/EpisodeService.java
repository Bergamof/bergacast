package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.audio.AudioFile;
import fr.bergadev.bergacast.audio.AudioUploads;
import fr.bergadev.bergacast.podcast.Podcast;
import fr.bergadev.bergacast.podcast.PodcastFeedChanged;
import fr.bergadev.bergacast.podcast.PodcastService;
import fr.bergadev.bergacast.storage.AfterCommit;
import fr.bergadev.bergacast.web.ConflictException;
import fr.bergadev.bergacast.web.EntityTags;
import fr.bergadev.bergacast.web.JsonMergePatch;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import jakarta.validation.groups.Default;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Use cases of episode publication and administration. */
@Service
@Transactional
public class EpisodeService {

    private final EpisodeRepository episodeRepository;
    private final PodcastService podcastService;
    private final AudioUploads audioUploads;
    private final JsonMergePatch jsonMergePatch;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EpisodeService(
            EpisodeRepository episodeRepository,
            PodcastService podcastService,
            AudioUploads audioUploads,
            JsonMergePatch jsonMergePatch,
            ApplicationEventPublisher events,
            Clock clock) {
        this.episodeRepository = episodeRepository;
        this.podcastService = podcastService;
        this.audioUploads = audioUploads;
        this.jsonMergePatch = jsonMergePatch;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Checks that an episode can be published, before its audio file is uploaded.
     *
     * @return the episode previously published with the same idempotency key, if any
     * @throws ResourceNotFoundException when there is no such podcast
     * @throws ConflictException when another episode of the podcast already has the same guid
     */
    @Transactional(readOnly = true)
    public Optional<Episode> prepare(String slug, EpisodeMetadata metadata, @Nullable String idempotencyKey) {
        Podcast podcast = podcastService.getBySlug(slug);
        Optional<Episode> alreadyPublished = findByIdempotencyKey(podcast, idempotencyKey);
        if (alreadyPublished.isEmpty()) {
            requireUniqueGuid(podcast, metadata.guid());
        }
        return alreadyPublished;
    }

    /**
     * Publishes an episode whose audio file is already in the object storage, or returns the episode published
     * meanwhile with the same idempotency key. The checks of {@link #prepare} are made again, since the data may have
     * changed while the audio file was uploaded; the feed is written again once the episode is committed.
     *
     * @throws ConflictException when another episode of the podcast already has the same guid
     */
    public Episode publish(
            String slug,
            UUID episodeId,
            EpisodeMetadata metadata,
            AudioFile audioFile,
            Instant addedAt,
            @Nullable String idempotencyKey) {
        Podcast podcast = podcastService.getBySlug(slug);
        Optional<Episode> alreadyPublished = findByIdempotencyKey(podcast, idempotencyKey);
        if (alreadyPublished.isPresent()) {
            return alreadyPublished.get();
        }
        requireUniqueGuid(podcast, metadata.guid());
        Episode episode = episodeRepository.saveAndFlush(
                new Episode(episodeId, podcast, audioFile, metadata, addedAt, idempotencyKey));
        events.publishEvent(new PodcastFeedChanged(slug));
        return episode;
    }

    /** Returns the episode published with an idempotency key, if any. */
    @Transactional(readOnly = true)
    public Optional<Episode> findByIdempotencyKey(String slug, String idempotencyKey) {
        return findByIdempotencyKey(podcastService.getBySlug(slug), idempotencyKey);
    }

    private Optional<Episode> findByIdempotencyKey(Podcast podcast, @Nullable String idempotencyKey) {
        return Optional.ofNullable(idempotencyKey)
                .flatMap(key -> episodeRepository.findByPodcastAndIdempotencyKey(podcast, key));
    }

    @Transactional(readOnly = true)
    public Page<Episode> list(String slug, Pageable pageable) {
        return episodeRepository.findByPodcast(podcastService.getBySlug(slug), pageable);
    }

    /** Returns all episodes of a podcast with their audio file, published or not. */
    @Transactional(readOnly = true)
    public List<Episode> listAll(Podcast podcast) {
        return episodeRepository.findAllByPodcast(podcast);
    }

    /**
     * Returns an episode of the given podcast.
     *
     * @throws ResourceNotFoundException when the podcast or the episode does not exist, or they do not match
     */
    @Transactional(readOnly = true)
    public Episode get(String slug, UUID episodeId) {
        return episodeRepository
                .findByIdAndPodcast(episodeId, podcastService.getBySlug(slug))
                .orElseThrow(
                        () -> new ResourceNotFoundException("No episode " + episodeId + " in podcast '" + slug + "'"));
    }

    /**
     * Applies a JSON Merge Patch to the metadata of an episode (the audio file cannot be replaced).
     *
     * @param ifMatch optional {@code If-Match} header value, checked against the current entity tag
     */
    public Episode patch(String slug, UUID episodeId, JsonNode patch, @Nullable String ifMatch) {
        Episode episode = get(slug, episodeId);
        EntityTags.requireIfMatch(ifMatch, entityTag(episode));
        EpisodeMetadata current = episode.metadata();
        EpisodeInput patched = jsonMergePatch.apply(
                EpisodeInput.from(current), patch, EpisodeInput.class, Default.class, EpisodeInput.Update.class);
        EpisodeMetadata updated = patched.toMetadata(clock.instant());
        if (!current.guid().equals(updated.guid())) {
            requireUniqueGuid(episode.getPodcast(), updated.guid());
        }
        episode.update(updated);
        Episode saved = episodeRepository.saveAndFlush(episode);
        events.publishEvent(new PodcastFeedChanged(slug));
        return saved;
    }

    /**
     * Deletes an episode (its audio file row is deleted by a database trigger); once committed, the feed is written
     * again and the audio object is removed from the storage.
     */
    public void delete(String slug, UUID episodeId) {
        Episode episode = get(slug, episodeId);
        String audioExtension = episode.getAudioFile().getFileExtension();
        episodeRepository.delete(episode);
        // Registered first: the audio object goes even if writing the feed then fails.
        AfterCommit.run(() -> audioUploads.discard(slug, episodeId, audioExtension));
        events.publishEvent(new PodcastFeedChanged(slug));
    }

    public static String entityTag(Episode episode) {
        return EntityTags.strong(episode.getVersion());
    }

    private void requireUniqueGuid(Podcast podcast, String guid) {
        if (episodeRepository.existsByPodcastAndGuid(podcast, guid)) {
            throw new ConflictException("An episode of this podcast already has the guid '" + guid + "'");
        }
    }
}

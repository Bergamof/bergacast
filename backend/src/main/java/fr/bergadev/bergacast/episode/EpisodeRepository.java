package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.podcast.Podcast;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of {@link Episode}s. */
public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    String AUDIO_FILE = "audioFile";

    @EntityGraph(attributePaths = AUDIO_FILE)
    Page<Episode> findByPodcast(Podcast podcast, Pageable pageable);

    @EntityGraph(attributePaths = AUDIO_FILE)
    List<Episode> findAllByPodcast(Podcast podcast);

    @EntityGraph(attributePaths = AUDIO_FILE)
    Optional<Episode> findByIdAndPodcast(UUID id, Podcast podcast);

    @EntityGraph(attributePaths = AUDIO_FILE)
    Optional<Episode> findByPodcastAndIdempotencyKey(Podcast podcast, String idempotencyKey);

    boolean existsByPodcastAndGuid(Podcast podcast, String guid);
}

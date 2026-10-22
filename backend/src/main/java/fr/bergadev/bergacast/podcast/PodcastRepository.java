package fr.bergadev.bergacast.podcast;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistence of {@link Podcast}s. */
public interface PodcastRepository extends JpaRepository<Podcast, UUID> {

    Optional<Podcast> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("""
            select new fr.bergadev.bergacast.podcast.EpisodeCount(e.podcast.id, count(e))
            from Episode e
            where e.podcast.id in :podcastIds
            group by e.podcast.id""")
    List<EpisodeCount> countEpisodes(Collection<UUID> podcastIds);
}

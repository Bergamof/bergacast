package fr.bergadev.bergacast.audio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Read access to {@link AudioFile}s; they are created and deleted along with their episode. */
interface AudioFileRepository extends JpaRepository<AudioFile, UUID> {

    @Query("""
            select e.audioFile from Episode e
            where e.id = :episodeId and e.podcast.slug = :podcastSlug""")
    Optional<AudioFile> findByEpisode(String podcastSlug, UUID episodeId);
}

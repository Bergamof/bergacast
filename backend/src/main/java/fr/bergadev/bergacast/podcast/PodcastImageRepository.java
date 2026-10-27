package fr.bergadev.bergacast.podcast;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence of the metadata of {@link PodcastImage}s. */
interface PodcastImageRepository extends JpaRepository<PodcastImage, UUID> {

    @Query("""
            select new fr.bergadev.bergacast.podcast.PodcastImageMetadata(
                i.podcastId, i.mimeType, i.width, i.height, i.sizeBytes, i.sha256)
            from PodcastImage i
            where i.podcastId = :podcastId""")
    Optional<PodcastImageMetadata> findMetadata(UUID podcastId);

    @Query("""
            select new fr.bergadev.bergacast.podcast.PodcastImageMetadata(
                i.podcastId, i.mimeType, i.width, i.height, i.sizeBytes, i.sha256)
            from PodcastImage i
            where i.podcastId in :podcastIds""")
    List<PodcastImageMetadata> findAllMetadata(Collection<UUID> podcastIds);

    /** Deletes the image of a podcast without loading it; returns the number of deleted images (0 or 1). */
    @Modifying
    @Query("delete from PodcastImage i where i.podcastId = :podcastId")
    int deleteByPodcastId(UUID podcastId);
}

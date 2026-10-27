package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.image.ProcessedImage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Cover image uploaded for a podcast, identified by the podcast itself.
 *
 * <p>The content is an object of the storage (see {@link fr.bergadev.bergacast.storage.MediaKeys#podcastImage}). The
 * entity is only used to insert images: reads go through {@link PodcastImageRepository} projections.
 */
@Entity
@Table(name = "podcast_image")
class PodcastImage implements Persistable<UUID> {

    @Id
    @Column(name = "podcast_id", nullable = false, updatable = false)
    private UUID podcastId;

    @Column(name = "mime_type", nullable = false, updatable = false)
    private String mimeType;

    @Column(name = "width", nullable = false, updatable = false)
    private int width;

    @Column(name = "height", nullable = false, updatable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    /** The identifier is assigned (the podcast's), so the entity tells Spring Data whether it must be inserted. */
    @Transient
    private boolean isNew = true;

    protected PodcastImage() {
        // required by JPA
    }

    PodcastImage(UUID podcastId, ProcessedImage image, String sha256) {
        this.podcastId = podcastId;
        this.mimeType = image.format().mimeType();
        this.width = image.width();
        this.height = image.height();
        this.sizeBytes = image.content().length;
        this.sha256 = sha256;
    }

    @Override
    public UUID getId() {
        return podcastId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    PodcastImageMetadata metadata() {
        return new PodcastImageMetadata(podcastId, mimeType, width, height, sizeBytes, sha256);
    }
}

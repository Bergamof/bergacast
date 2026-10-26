package fr.bergadev.bergacast.podcast;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A podcast: its public metadata and the rules of its feed. The slug identifies it in URLs and never changes. */
@Entity
@Table(name = "podcast")
public class Podcast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, updatable = false, length = Slugs.MAX_LENGTH)
    private String slug;

    @Embedded
    private PodcastDetails details;

    @Embedded
    private FeedSettings feedSettings;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Podcast() {
        // required by JPA
    }

    public Podcast(String slug, PodcastDetails details, FeedSettings feedSettings, Instant createdAt) {
        this.slug = slug;
        this.details = details;
        this.feedSettings = feedSettings;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(PodcastDetails newDetails, FeedSettings newFeedSettings, Instant now) {
        this.details = newDetails;
        this.feedSettings = newFeedSettings;
        this.updatedAt = now;
    }

    /**
     * Records a change of data stored apart from the podcast but part of its representation (its cover image), so
     * that its version, hence its entity tag, changes too.
     */
    void markUpdated(Instant now) {
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public PodcastDetails getDetails() {
        return details;
    }

    public FeedSettings getFeedSettings() {
        return feedSettings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}

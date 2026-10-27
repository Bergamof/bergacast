package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.audio.AudioFile;
import fr.bergadev.bergacast.podcast.Podcast;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An episode of a podcast and its audio file.
 *
 * <p>Deleting an episode deletes its audio file row (database trigger), hence the audio file is only cascaded on
 * persist. The identifier is assigned on creation: the object key of the audio file, written first, holds it.
 */
@Entity
@Table(name = "episode")
public class Episode {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "podcast_id", nullable = false, updatable = false)
    private Podcast podcast;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "audio_file_id", nullable = false, updatable = false, unique = true)
    private AudioFile audioFile;

    @Column(name = "guid", nullable = false)
    private String guid;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private @Nullable String description;

    @Column(name = "publication_date", nullable = false)
    private Instant publicationDate;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "duration_seconds")
    private @Nullable Integer durationSeconds;

    @Column(name = "season")
    private @Nullable Integer season;

    @Column(name = "episode_number")
    private @Nullable Integer episodeNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "episode_type", nullable = false, length = 16)
    private EpisodeType episodeType;

    @Column(name = "explicit")
    private @Nullable Boolean explicit;

    @Column(name = "idempotency_key", updatable = false)
    private @Nullable String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Episode() {
        // required by JPA
    }

    public Episode(
            UUID id,
            Podcast podcast,
            AudioFile audioFile,
            EpisodeMetadata metadata,
            Instant addedAt,
            @Nullable String idempotencyKey) {
        this.id = id;
        this.podcast = podcast;
        this.audioFile = audioFile;
        this.addedAt = addedAt;
        this.idempotencyKey = idempotencyKey;
        apply(metadata);
    }

    public void update(EpisodeMetadata metadata) {
        apply(metadata);
    }

    private void apply(EpisodeMetadata metadata) {
        this.guid = metadata.guid();
        this.title = metadata.title();
        this.description = metadata.description();
        this.publicationDate = metadata.publicationDate();
        this.durationSeconds = metadata.durationSeconds();
        this.season = metadata.season();
        this.episodeNumber = metadata.episodeNumber();
        this.episodeType = metadata.episodeType();
        this.explicit = metadata.explicit();
    }

    public EpisodeMetadata metadata() {
        return new EpisodeMetadata(
                guid,
                title,
                description,
                publicationDate,
                durationSeconds,
                season,
                episodeNumber,
                episodeType,
                explicit);
    }

    public UUID getId() {
        return id;
    }

    public Podcast getPodcast() {
        return podcast;
    }

    public AudioFile getAudioFile() {
        return audioFile;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public Long getVersion() {
        return version;
    }
}

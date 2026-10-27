package fr.bergadev.bergacast.audio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Description of the audio file of an episode, whose content is an object of the storage (see
 * {@link fr.bergadev.bergacast.storage.MediaKeys#episodeAudio}). Immutable once created.
 */
@Entity
@Table(name = "audio_file")
public class AudioFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 127)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "file_extension", nullable = false, updatable = false, length = 16)
    private String fileExtension;

    protected AudioFile() {
        // required by JPA
    }

    AudioFile(String mimeType, long sizeBytes, String sha256, String fileExtension) {
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.fileExtension = fileExtension;
    }

    public UUID getId() {
        return id;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}

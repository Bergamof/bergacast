package io.github.bergamof.bergacast.audio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Audio content of an episode, stored in a PostgreSQL large object. Immutable once created.
 *
 * <p>Deleting the row unlinks the large object (database trigger), see {@code V1__create_schema.sql}.
 */
@Entity
@Table(name = "audio_file")
public class AudioFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content_oid", nullable = false, updatable = false, columnDefinition = "oid")
    private long contentOid;

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

    AudioFile(StoredContent content, String mimeType, String fileExtension) {
        this.contentOid = content.oid();
        this.sizeBytes = content.sizeBytes();
        this.sha256 = content.sha256();
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
    }

    public UUID getId() {
        return id;
    }

    public long getContentOid() {
        return contentOid;
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

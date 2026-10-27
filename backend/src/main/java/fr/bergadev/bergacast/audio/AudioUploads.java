package fr.bergadev.bergacast.audio;

import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.storage.ObjectStorage;
import fr.bergadev.bergacast.web.Sha256;
import fr.bergadev.bergacast.web.UnsupportedMediaTypeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Writes uploaded audio files to the object storage, where podcast apps download them. */
@Service
public class AudioUploads {

    private static final String AUDIO_TYPE = "audio";
    private static final int BUFFER_SIZE = 64 * 1024;
    /** The content of an episode never changes (its key holds the episode id). */
    private static final CacheControl IMMUTABLE_FOR_A_YEAR =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final ObjectStorage storage;

    AudioUploads(ObjectStorage storage) {
        this.storage = storage;
    }

    /**
     * Writes the audio file of an episode to the object storage. The returned entity is not persisted yet; if it
     * never is, the object must be removed with {@link #discard}.
     *
     * <p>Called outside of any transaction: no database connection is held while the file is uploaded.
     *
     * @throws UnsupportedMediaTypeException when the part is not declared as {@code audio/*}
     * @throws fr.bergadev.bergacast.storage.ObjectStorageException when the storage fails
     */
    public AudioFile upload(String podcastSlug, UUID episodeId, MultipartFile upload) {
        MediaType audioType = audioTypeOf(upload);
        String mimeType = audioType.getType() + "/" + audioType.getSubtype();
        AudioFile audioFile = new AudioFile(
                mimeType,
                upload.getSize(),
                sha256Of(upload),
                AudioFormats.extensionFor(audioType, upload.getOriginalFilename()));
        String key = MediaKeys.episodeAudio(podcastSlug, episodeId, audioFile.getFileExtension());
        storage.put(key, upload, upload.getSize(), mimeType, IMMUTABLE_FOR_A_YEAR);
        return audioFile;
    }

    /** Removes the object of an audio file that is not, or no longer, referenced by an episode. */
    public void discard(String podcastSlug, UUID episodeId, String fileExtension) {
        storage.discard(MediaKeys.episodeAudio(podcastSlug, episodeId, fileExtension));
    }

    /** The part is already on disk (multipart file-size-threshold of 0): it is read once more to upload it. */
    private static String sha256Of(MultipartFile upload) {
        MessageDigest digest = Sha256.newDigest();
        try (InputStream content = upload.getInputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = content.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read the uploaded audio file", exception);
        }
        return Sha256.hex(digest);
    }

    private static MediaType audioTypeOf(MultipartFile upload) {
        String contentType = upload.getContentType();
        MediaType mediaType = parseOrNull(contentType);
        if (mediaType == null || !AUDIO_TYPE.equalsIgnoreCase(mediaType.getType()) || mediaType.isWildcardSubtype()) {
            throw new UnsupportedMediaTypeException(
                    "The audio part must have an audio/* content type, got '" + contentType + "'");
        }
        return mediaType;
    }

    private static @Nullable MediaType parseOrNull(@Nullable String contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException invalid) {
            return null;
        }
    }
}

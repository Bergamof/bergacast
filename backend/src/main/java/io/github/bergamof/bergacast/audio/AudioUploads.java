package io.github.bergamof.bergacast.audio;

import io.github.bergamof.bergacast.web.UnsupportedMediaTypeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Turns an uploaded audio file into an {@link AudioFile} whose content is stored in the database. */
@Service
public class AudioUploads {

    private static final String AUDIO_TYPE = "audio";

    private final LargeObjectStorage storage;

    AudioUploads(LargeObjectStorage storage) {
        this.storage = storage;
    }

    /**
     * Streams the upload into a large object. The returned entity is not persisted yet: it must be saved in the same
     * transaction, otherwise the large object is discarded with the transaction.
     *
     * @throws UnsupportedMediaTypeException when the part is not declared as {@code audio/*}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AudioFile store(MultipartFile upload) {
        MediaType audioType = audioTypeOf(upload);
        try (InputStream content = upload.getInputStream()) {
            StoredContent stored = storage.store(content);
            String mimeType = audioType.getType() + "/" + audioType.getSubtype();
            return new AudioFile(stored, mimeType, AudioFormats.extensionFor(audioType, upload.getOriginalFilename()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot store the uploaded audio file", exception);
        }
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

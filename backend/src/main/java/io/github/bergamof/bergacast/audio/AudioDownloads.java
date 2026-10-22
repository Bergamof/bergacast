package io.github.bergamof.bergacast.audio;

import io.github.bergamof.bergacast.web.ResourceNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Read access to the audio content of episodes. */
@Service
public class AudioDownloads {

    /** Bytes read per database round trip: bounds both memory use and connection hold time. */
    static final int CHUNK_SIZE = 1024 * 1024;

    private final AudioFileRepository audioFileRepository;
    private final LargeObjectStorage storage;
    private final TransactionTemplate readOnlyTransaction;

    AudioDownloads(
            AudioFileRepository audioFileRepository,
            LargeObjectStorage storage,
            PlatformTransactionManager transactionManager) {
        this.audioFileRepository = audioFileRepository;
        this.storage = storage;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * Returns the audio file of an episode of the given podcast.
     *
     * @throws ResourceNotFoundException when the episode does not exist or belongs to another podcast
     */
    @Transactional(readOnly = true)
    public AudioFile find(String podcastSlug, UUID episodeId) {
        return audioFileRepository
                .findByEpisode(podcastSlug, episodeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("No episode " + episodeId + " in podcast '" + podcastSlug + "'"));
    }

    /**
     * Streams {@code length} bytes of the content from {@code offset}. Each chunk is read in its own short
     * transaction and written to {@code target} outside of any transaction, so that slow clients never hold a
     * database connection.
     *
     * @throws AudioContentUnavailableException when the content disappears, e.g. its episode was deleted meanwhile
     */
    public void write(AudioFile audioFile, long offset, long length, OutputStream target) throws IOException {
        long end = offset + length;
        long position = offset;
        while (position < end) {
            long chunkOffset = position;
            int chunkLength = (int) Math.min(CHUNK_SIZE, end - position);
            byte[] chunk = Objects.requireNonNull(readOnlyTransaction.execute(
                    status -> storage.read(audioFile.getContentOid(), chunkOffset, chunkLength)));
            if (chunk.length == 0) {
                throw new IOException("Audio content " + audioFile.getId() + " is shorter than expected");
            }
            target.write(chunk);
            position += chunk.length;
        }
    }
}

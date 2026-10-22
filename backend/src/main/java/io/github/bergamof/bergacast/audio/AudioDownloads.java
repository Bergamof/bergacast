package io.github.bergamof.bergacast.audio;

import io.github.bergamof.bergacast.web.ResourceNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to the audio content of episodes. */
@Service
@Transactional(readOnly = true)
public class AudioDownloads {

    private final AudioFileRepository audioFileRepository;
    private final LargeObjectStorage storage;

    AudioDownloads(AudioFileRepository audioFileRepository, LargeObjectStorage storage) {
        this.audioFileRepository = audioFileRepository;
        this.storage = storage;
    }

    /**
     * Returns the audio file of an episode of the given podcast.
     *
     * @throws ResourceNotFoundException when the episode does not exist or belongs to another podcast
     */
    public AudioFile find(String podcastSlug, UUID episodeId) {
        return audioFileRepository
                .findByEpisode(podcastSlug, episodeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("No episode " + episodeId + " in podcast '" + podcastSlug + "'"));
    }

    /** Streams {@code length} bytes of the content from {@code offset}, inside a read-only transaction. */
    public void write(AudioFile audioFile, long offset, long length, OutputStream target) throws IOException {
        storage.copy(audioFile.getContentOid(), offset, length, target);
    }
}

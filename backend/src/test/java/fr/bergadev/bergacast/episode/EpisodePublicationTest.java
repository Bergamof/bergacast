package fr.bergadev.bergacast.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.bergadev.bergacast.audio.AudioFile;
import fr.bergadev.bergacast.audio.AudioUploads;
import fr.bergadev.bergacast.feed.FeedPublicationException;
import fr.bergadev.bergacast.storage.ObjectStorageException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class EpisodePublicationTest {

    private static final String SLUG = "show";
    private static final String KEY = "upload-1";
    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final EpisodeInput INPUT =
            new EpisodeInput("Title", null, null, "guid-1", null, null, null, null, null);
    private static final EpisodeMetadata METADATA = INPUT.toMetadata(NOW);
    private static final MultipartFile AUDIO = new MockMultipartFile("audio", "a.mp3", "audio/mpeg", new byte[] {1});

    private final EpisodeService episodeService = mock(EpisodeService.class);
    private final AudioUploads audioUploads = mock(AudioUploads.class);
    private final AudioFile audioFile = mock(AudioFile.class);
    private final EpisodePublication publication =
            new EpisodePublication(episodeService, audioUploads, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void uploadSucceeds() {
        when(audioFile.getFileExtension()).thenReturn("mp3");
        when(audioUploads.upload(eq(SLUG), any(UUID.class), eq(AUDIO))).thenReturn(audioFile);
    }

    @Test
    void uploadsTheAudioThenSavesTheEpisodeWithTheSameId() {
        whenSaved(KEY).thenAnswer(invocation -> episodeWithId(invocation.getArgument(1)));

        Episode published = publication.publish(SLUG, INPUT, AUDIO, KEY);

        ArgumentCaptor<UUID> uploadedId = ArgumentCaptor.forClass(UUID.class);
        verify(audioUploads).upload(eq(SLUG), uploadedId.capture(), eq(AUDIO));
        verify(episodeService).publish(SLUG, uploadedId.getValue(), METADATA, audioFile, NOW, KEY);
        assertThat(published.getId()).isEqualTo(uploadedId.getValue());
        verify(audioUploads, never()).discard(anyString(), any(UUID.class), anyString());
    }

    @Test
    void discardsItsAudioWhenAnEpisodePublishedMeanwhileWithTheSameKeyIsReplayed() {
        Episode publishedMeanwhile = episodeWithId(UUID.randomUUID());
        whenSaved(KEY).thenReturn(publishedMeanwhile);

        assertThat(publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(publishedMeanwhile);
        verifyAudioDiscarded();
    }

    @Test
    void replaysAnEpisodeAlreadyPublishedWithTheSameKeyWithoutUploadingTheAudio() {
        Episode previous = mock(Episode.class);
        when(episodeService.prepare(SLUG, METADATA, KEY)).thenReturn(Optional.of(previous));

        assertThat(publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(previous);
        verifyNoInteractions(audioUploads);
        verify(episodeService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void savesNothingWhenTheAudioCannotBeUploaded() {
        ObjectStorageException storageDown = new ObjectStorageException("down", new RuntimeException());
        when(audioUploads.upload(eq(SLUG), any(UUID.class), eq(AUDIO))).thenThrow(storageDown);

        assertThatThrownBy(() -> publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(storageDown);
        verify(episodeService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void discardsTheAudioWhenTheEpisodeCannotBeSaved() {
        IllegalStateException failure = new IllegalStateException("database down");
        whenSaved(KEY).thenThrow(failure);

        assertThatThrownBy(() -> publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(failure);
        verifyAudioDiscarded();
    }

    @Test
    void keepsTheAudioWhenOnlyTheFeedCannotBeWrittenAfterTheEpisodeIsSaved() {
        FeedPublicationException feedNotWritten = mock(FeedPublicationException.class);
        whenSaved(KEY).thenThrow(feedNotWritten);

        assertThatThrownBy(() -> publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(feedNotWritten);
        verify(audioUploads, never()).discard(anyString(), any(UUID.class), anyString());
    }

    @Test
    void replaysTheEpisodeOfAConcurrentRequestWithTheSameKeyAndDiscardsItsOwnAudio() {
        Episode winner = mock(Episode.class);
        whenSaved(KEY).thenThrow(violationOf(EpisodePublication.IDEMPOTENCY_KEY_CONSTRAINT));
        when(episodeService.findByIdempotencyKey(SLUG, KEY)).thenReturn(Optional.of(winner));

        assertThat(publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(winner);
        verifyAudioDiscarded();
    }

    @Test
    void propagatesOtherConstraintViolations() {
        DataIntegrityViolationException duplicateGuid = violationOf("episode_guid_unique");
        whenSaved(KEY).thenThrow(duplicateGuid);

        assertThatThrownBy(() -> publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(duplicateGuid);
        verifyAudioDiscarded();
    }

    @Test
    void propagatesViolationsOfRequestsWithoutKey() {
        DataIntegrityViolationException violation = violationOf(EpisodePublication.IDEMPOTENCY_KEY_CONSTRAINT);
        whenSaved(null).thenThrow(violation);

        assertThatThrownBy(() -> publication.publish(SLUG, INPUT, AUDIO, null)).isSameAs(violation);
        verifyAudioDiscarded();
    }

    private OngoingStubbing<Episode> whenSaved(@Nullable String idempotencyKey) {
        return when(episodeService.publish(
                eq(SLUG), any(UUID.class), eq(METADATA), eq(audioFile), eq(NOW), eq(idempotencyKey)));
    }

    private static Episode episodeWithId(UUID id) {
        Episode episode = mock(Episode.class);
        when(episode.getId()).thenReturn(id);
        return episode;
    }

    /** The object removed is the one uploaded, identified by the id given to the new episode. */
    private void verifyAudioDiscarded() {
        ArgumentCaptor<UUID> uploadedId = ArgumentCaptor.forClass(UUID.class);
        verify(audioUploads).upload(eq(SLUG), uploadedId.capture(), eq(AUDIO));
        verify(audioUploads).discard(SLUG, uploadedId.getValue(), "mp3");
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException(
                "duplicate key", new ConstraintViolationException("duplicate key", new SQLException(), constraint));
    }
}

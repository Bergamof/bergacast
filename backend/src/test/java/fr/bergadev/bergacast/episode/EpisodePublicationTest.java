package fr.bergadev.bergacast.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class EpisodePublicationTest {

    private static final String SLUG = "show";
    private static final String KEY = "upload-1";
    private static final EpisodeInput INPUT = new EpisodeInput("Title", null, null, null, null, null, null, null, null);
    private static final MultipartFile AUDIO = new MockMultipartFile("audio", "a.mp3", "audio/mpeg", new byte[] {1});

    private final EpisodeService episodeService = mock(EpisodeService.class);
    private final EpisodePublication publication = new EpisodePublication(episodeService);

    @Test
    void replaysTheEpisodeOfAConcurrentRequestWithTheSameKey() {
        Episode winner = mock(Episode.class);
        when(episodeService.publish(SLUG, INPUT, AUDIO, KEY))
                .thenThrow(violationOf(EpisodePublication.IDEMPOTENCY_KEY_CONSTRAINT));
        when(episodeService.findByIdempotencyKey(SLUG, KEY)).thenReturn(Optional.of(winner));

        assertThat(publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(winner);
    }

    @Test
    void propagatesOtherConstraintViolations() {
        DataIntegrityViolationException duplicateGuid = violationOf("episode_guid_unique");
        when(episodeService.publish(SLUG, INPUT, AUDIO, KEY)).thenThrow(duplicateGuid);

        assertThatThrownBy(() -> publication.publish(SLUG, INPUT, AUDIO, KEY)).isSameAs(duplicateGuid);
    }

    @Test
    void propagatesViolationsOfRequestsWithoutKey() {
        DataIntegrityViolationException violation = violationOf(EpisodePublication.IDEMPOTENCY_KEY_CONSTRAINT);
        when(episodeService.publish(SLUG, INPUT, AUDIO, null)).thenThrow(violation);

        assertThatThrownBy(() -> publication.publish(SLUG, INPUT, AUDIO, null)).isSameAs(violation);
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException(
                "duplicate key", new ConstraintViolationException("duplicate key", new SQLException(), constraint));
    }
}

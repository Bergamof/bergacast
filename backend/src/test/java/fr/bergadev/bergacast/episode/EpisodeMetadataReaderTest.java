package fr.bergadev.bergacast.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import fr.bergadev.bergacast.web.ContentTooLargeException;
import fr.bergadev.bergacast.web.JsonRequestSizeFilter;
import fr.bergadev.bergacast.web.RequestValidator;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import tools.jackson.databind.json.JsonMapper;

class EpisodeMetadataReaderTest {

    private final EpisodeMetadataReader reader =
            new EpisodeMetadataReader(JsonMapper.builder().build(), new RequestValidator(mock(Validator.class)));

    @Test
    void readsTheMetadataPart() {
        EpisodeInput input = reader.read(part("{\"title\":\"Episode 1\"}"));

        assertThat(input.title()).isEqualTo("Episode 1");
    }

    @Test
    void refusesAMetadataPartLargerThanTheJsonBodiesOfTheApi() {
        int limit = Math.toIntExact(JsonRequestSizeFilter.MAX_JSON_BODY.toBytes());

        assertThatThrownBy(() -> reader.read(part("{\"title\":\"" + "t".repeat(limit) + "\"}")))
                .isInstanceOf(ContentTooLargeException.class);
    }

    private static MockPart part(String json) {
        MockPart part = new MockPart("metadata", json.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }
}

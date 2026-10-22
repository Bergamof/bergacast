package io.github.bergamof.bergacast.episode;

import io.github.bergamof.bergacast.web.BadRequestException;
import io.github.bergamof.bergacast.web.RequestValidator;
import io.github.bergamof.bergacast.web.UnsupportedMediaTypeException;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the {@code metadata} part of an episode upload.
 *
 * <p>Automated tools (curl {@code -F}, n8n, Zapier, Python requests...) often send a JSON form field without content
 * type, or as {@code text/plain} or {@code application/octet-stream}: those are accepted and parsed as JSON.
 */
@Component
class EpisodeMetadataReader {

    private static final Set<MediaType> LENIENT_TYPES =
            Set.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN);
    private static final String JSON_SUFFIX = "+json";

    private final JsonMapper jsonMapper;
    private final RequestValidator requestValidator;

    EpisodeMetadataReader(JsonMapper jsonMapper, RequestValidator requestValidator) {
        this.jsonMapper = jsonMapper;
        this.requestValidator = requestValidator;
    }

    /**
     * @throws UnsupportedMediaTypeException when the part declares a content type that cannot hold JSON
     * @throws BadRequestException when the part is not a JSON object of the expected shape
     * @throws io.github.bergamof.bergacast.web.UnprocessableContentException when the metadata are invalid
     */
    EpisodeInput read(Part metadata) {
        requireJsonCompatible(metadata.getContentType());
        EpisodeInput input;
        try (InputStream content = metadata.getInputStream()) {
            input = jsonMapper.readValue(content, EpisodeInput.class);
        } catch (JacksonException malformed) {
            throw new BadRequestException("The metadata part must be a JSON object with values of the right types");
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read the metadata part", exception);
        }
        if (input == null) {
            throw new BadRequestException("The metadata part must be a JSON object");
        }
        requestValidator.validate(input);
        return input;
    }

    private static void requireJsonCompatible(@Nullable String contentType) {
        if (contentType == null) {
            return;
        }
        MediaType mediaType = parseOrNull(contentType);
        boolean accepted = mediaType != null
                && (LENIENT_TYPES.stream().anyMatch(type -> type.equalsTypeAndSubtype(mediaType))
                        || ("application".equals(mediaType.getType())
                                && mediaType.getSubtype().endsWith(JSON_SUFFIX)));
        if (!accepted) {
            throw new UnsupportedMediaTypeException(
                    "The metadata part must contain JSON, got content type '" + contentType + "'");
        }
    }

    private static @Nullable MediaType parseOrNull(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException invalid) {
            return null;
        }
    }
}

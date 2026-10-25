package fr.bergadev.bergacast.audio;

import fr.bergadev.bergacast.web.EntityTagged;
import fr.bergadev.bergacast.web.RangeNotSatisfiableException;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Public download of episode audio, with single byte range support for players that seek.
 *
 * <p>Audio of episodes not published yet is served as well: its URL is unguessable until it appears in the feed.
 */
@RestController
@Tag(name = "Public feed")
class AudioController {

    static final String AUDIO_PATH = "/{slug}/episodes/{episodeId}/audio.{extension}";
    private static final String BYTES_UNIT = "bytes";
    private static final String RANGE_DESCRIPTION = "Single byte range to download (RFC 9110), e.g. bytes=0-1023 or "
            + "bytes=-1024 (the last 1024 bytes). Several ranges, or another unit, are ignored: the whole file is "
            + "served.";
    private static final CacheControl IMMUTABLE_FOR_A_YEAR =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private static final Logger LOGGER = LoggerFactory.getLogger(AudioController.class);

    private final AudioDownloads audioDownloads;

    AudioController(AudioDownloads audioDownloads) {
        this.audioDownloads = audioDownloads;
    }

    @RequestMapping(
            path = AUDIO_PATH,
            method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(
            operationId = "downloadEpisodeAudio",
            summary = "Download the audio of an episode",
            description = "Public. The audio file of an episode, as linked by the feed (enclosure) and by the "
                    + "audio.url of the episode, with the content type declared on upload. Supports a single byte "
                    + "range (Range header) for players that seek, HEAD, and conditional requests with "
                    + "If-None-Match; cacheable for a year (the content of an episode never changes). The audio of an "
                    + "episode not published yet is served as well: its URL only becomes known when it appears in "
                    + "the feed.")
    @Parameter(
            name = "extension",
            in = ParameterIn.PATH,
            required = true,
            description = "File extension derived from the audio type on upload (e.g. mp3 for audio/mpeg), which some "
                    + "podcast apps rely on. Informative only: any value serves the same file.",
            example = "mp3",
            schema = @Schema(type = "string"))
    @ApiResponse(
            responseCode = "200",
            description = "The whole audio file",
            headers =
                    @Header(
                            name = HttpHeaders.ACCEPT_RANGES,
                            description = "Always bytes: byte ranges are supported",
                            schema = @Schema(type = "string")),
            content = @Content(mediaType = "audio/*", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(
            responseCode = "206",
            description = "The requested byte range",
            headers =
                    @Header(
                            name = HttpHeaders.CONTENT_RANGE,
                            description = "Position of the returned range in the file, e.g. bytes 0-1023/43393024",
                            schema = @Schema(type = "string")),
            content = @Content(mediaType = "audio/*", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(
            responseCode = "304",
            description = "Not modified since the ETag sent in If-None-Match",
            content = @Content)
    @ApiResponse(
            responseCode = "416",
            description = "Range not satisfiable, e.g. it starts beyond the end of the file",
            content = @Content)
    @EntityTagged
    void download(
            @PathVariable String slug,
            @PathVariable UUID episodeId,
            @Parameter(description = RANGE_DESCRIPTION)
                    @RequestHeader(name = HttpHeaders.RANGE, required = false)
                    @Nullable
                    String range,
            ServletWebRequest webRequest,
            HttpServletResponse response)
            throws IOException {
        AudioFile audio = audioDownloads.find(slug, episodeId);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT);
        response.setHeader(HttpHeaders.CACHE_CONTROL, IMMUTABLE_FOR_A_YEAR.getHeaderValue());
        if (webRequest.checkNotModified("\"" + audio.getSha256() + "\"")) {
            return;
        }
        long offset;
        long length;
        switch (ByteRangeParser.parse(range, audio.getSizeBytes())) {
            case ByteRange.Full full -> {
                response.setStatus(HttpStatus.OK.value());
                offset = 0;
                length = full.length();
            }
            case ByteRange.Partial partial -> {
                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                response.setHeader(HttpHeaders.CONTENT_RANGE, partial.contentRange());
                offset = partial.first();
                length = partial.length();
            }
            case ByteRange.Unsatisfiable unsatisfiable -> {
                response.setHeader(HttpHeaders.CACHE_CONTROL, null);
                throw new RangeNotSatisfiableException(unsatisfiable.completeLength());
            }
        }
        response.setContentType(audio.getMimeType());
        response.setContentLengthLong(length);
        if (HttpMethod.HEAD.matches(webRequest.getRequest().getMethod())) {
            return;
        }
        try {
            audioDownloads.write(audio, offset, length, response.getOutputStream());
        } catch (AudioContentUnavailableException gone) {
            if (response.isCommitted()) {
                LOGGER.warn("Audio of episode {} disappeared during a download, response truncated", episodeId);
                return;
            }
            response.reset();
            throw new ResourceNotFoundException("No audio for episode " + episodeId);
        }
    }
}

package io.github.bergamof.bergacast.audio;

import io.github.bergamof.bergacast.web.RangeNotSatisfiableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
@Tag(name = "Public feed", description = "Public RSS feeds and audio files")
class AudioController {

    static final String AUDIO_PATH = "/{slug}/episodes/{episodeId}/audio.{extension}";
    private static final String BYTES_UNIT = "bytes";
    private static final CacheControl IMMUTABLE_FOR_A_YEAR =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

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
            description = "Public. Supports a single byte range (Range header) and If-None-Match. The extension is "
                    + "informative only.")
    @Parameter(
            name = "extension",
            in = ParameterIn.PATH,
            required = true,
            description = "File extension of the audio format, informative only",
            schema = @Schema(type = "string"))
    @ApiResponse(
            responseCode = "200",
            description = "The whole audio file",
            content = @Content(mediaType = "audio/*", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(
            responseCode = "206",
            description = "The requested byte range",
            content = @Content(mediaType = "audio/*", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    @ApiResponse(responseCode = "416", description = "Range not satisfiable", content = @Content)
    void download(
            @PathVariable String slug,
            @PathVariable UUID episodeId,
            @RequestHeader(name = HttpHeaders.RANGE, required = false) @Nullable String range,
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
        audioDownloads.write(audio, offset, length, response.getOutputStream());
    }
}

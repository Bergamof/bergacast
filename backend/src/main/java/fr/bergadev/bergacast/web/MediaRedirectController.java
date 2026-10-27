package fr.bergadev.bergacast.web;

import fr.bergadev.bergacast.storage.MediaKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feeds, audio files and images used to be served by this server: their former URLs, still known by subscribed
 * podcast apps, are permanently redirected to the same path on the object storage. The target only depends on the
 * path, so the database is never queried; a missing object is reported by the storage.
 */
@RestController
@Tag(name = "Public feed")
class MediaRedirectController {

    private static final String SLUG = "{slug:[a-z0-9-]+}";
    private static final String MOVED = "Moved permanently to the object storage";
    private static final String LOCATION_DESCRIPTION = "Same path on the object storage (BERGACAST_MEDIA_BASE_URL)";
    /** Permanent: clients may remember it, a day bounds how long a misconfigured target would stick. */
    private static final CacheControl ONE_DAY =
            CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final PublicUrls publicUrls;

    MediaRedirectController(PublicUrls publicUrls) {
        this.publicUrls = publicUrls;
    }

    @RequestMapping(
            path = "/" + SLUG + ".xml",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(
            operationId = "redirectPodcastFeed",
            summary = "Redirect to the RSS feed of a podcast",
            description = "Public. Former URL of the feed: redirected to the feed written to the object storage. "
                    + "Podcast apps follow the redirection and usually remember the new URL.")
    @ApiResponse(
            responseCode = "301",
            description = MOVED,
            headers =
                    @Header(
                            name = HttpHeaders.LOCATION,
                            description = LOCATION_DESCRIPTION,
                            schema = @Schema(type = "string")),
            content = @Content)
    ResponseEntity<Void> feed(@Parameter(description = "Slug of the podcast") @PathVariable String slug) {
        return redirect(MediaKeys.feed(slug));
    }

    @RequestMapping(
            path = "/" + SLUG + "/image.{extension:jpg|png}",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(
            operationId = "redirectPodcastImage",
            summary = "Redirect to the cover image of a podcast",
            description = "Public. Former URL of the uploaded cover image: redirected to the object storage.")
    @ApiResponse(
            responseCode = "301",
            description = MOVED,
            headers =
                    @Header(
                            name = HttpHeaders.LOCATION,
                            description = LOCATION_DESCRIPTION,
                            schema = @Schema(type = "string")),
            content = @Content)
    ResponseEntity<Void> image(
            @Parameter(description = "Slug of the podcast") @PathVariable String slug,
            @Parameter(description = "File extension of the image format (jpg or png)") @PathVariable
                    String extension) {
        return redirect(MediaKeys.podcastImage(slug, extension));
    }

    @RequestMapping(
            path = "/" + SLUG + "/episodes/{episodeId}/audio.{extension:[a-z0-9]+}",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(
            operationId = "redirectEpisodeAudio",
            summary = "Redirect to the audio of an episode",
            description = "Public. Former URL of the audio file of an episode (feed enclosure): redirected to the "
                    + "object storage.")
    @ApiResponse(
            responseCode = "301",
            description = MOVED,
            headers =
                    @Header(
                            name = HttpHeaders.LOCATION,
                            description = LOCATION_DESCRIPTION,
                            schema = @Schema(type = "string")),
            content = @Content)
    ResponseEntity<Void> audio(
            @Parameter(description = "Slug of the podcast") @PathVariable String slug,
            @Parameter(description = "Identifier of the episode") @PathVariable UUID episodeId,
            @Parameter(description = "File extension of the audio file, e.g. mp3") @PathVariable String extension) {
        return redirect(MediaKeys.episodeAudio(slug, episodeId, extension));
    }

    private ResponseEntity<Void> redirect(String key) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(publicUrls.media(key))
                .cacheControl(ONE_DAY)
                .build();
    }
}

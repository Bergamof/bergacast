package fr.bergadev.bergacast.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keeps search engines away: the podcasts are meant for the subscribers who know their feed URL. Every response also
 * carries {@link #X_ROBOTS_TAG} (see the security configuration). Podcast apps fetch feeds and audio regardless.
 */
@RestController
@Tag(name = "Robots")
public class RobotsController {

    public static final String X_ROBOTS_TAG = "X-Robots-Tag";
    public static final String NO_INDEX = "noindex, nofollow";
    static final String ROBOTS_TXT = "User-agent: *\nDisallow: /\n";
    private static final CacheControl ONE_DAY =
            CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    @GetMapping(path = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(
            operationId = "getRobotsTxt",
            summary = "Get the crawling rules for robots",
            description = "Public. robots.txt (RFC 9309) disallowing the crawling of the whole server; every "
                    + "response also carries `X-Robots-Tag: noindex, nofollow`. Podcast apps fetching a feed or "
                    + "an audio file are not crawlers and are not affected.")
    @ApiResponse(
            responseCode = "200",
            description = "The robots.txt rules",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(type = "string")))
    public ResponseEntity<String> robotsTxt() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .cacheControl(ONE_DAY)
                .body(ROBOTS_TXT);
    }
}

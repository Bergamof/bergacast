package io.github.bergamof.bergacast.feed;

import io.github.bergamof.bergacast.web.Sha256;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public RSS feed of a podcast. */
@RestController
@Tag(name = "Public feed", description = "Public RSS feeds and audio files")
class FeedController {

    static final String RSS_XML_VALUE = "application/rss+xml;charset=UTF-8";
    static final MediaType RSS_XML = MediaType.parseMediaType(RSS_XML_VALUE);
    private static final CacheControl FIVE_MINUTES =
            CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final FeedService feedService;

    FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/{slug:[a-z0-9-]+}.xml")
    @Operation(
            operationId = "getPodcastFeed",
            summary = "Get the RSS feed of a podcast",
            description = "Public. RSS 2.0 with Apple Podcasts extensions. Supports If-None-Match.")
    @ApiResponse(
            responseCode = "200",
            description = "The feed",
            content = @Content(mediaType = RSS_XML_VALUE, schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    ResponseEntity<byte[]> feed(@PathVariable String slug) {
        byte[] document = feedService.render(slug);
        return ResponseEntity.ok()
                .contentType(RSS_XML)
                .cacheControl(FIVE_MINUTES)
                .eTag("\"" + Sha256.hex(document) + "\"")
                .body(document);
    }
}

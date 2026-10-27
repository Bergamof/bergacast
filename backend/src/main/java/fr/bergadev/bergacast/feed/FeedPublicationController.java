package fr.bergadev.bergacast.feed;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Writes the feed of a podcast on demand, besides the automatic writes that follow each change. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Feeds")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class FeedPublicationController {

    static final LinkRelation PODCAST = LinkRelation.of("podcast");
    static final LinkRelation FEED = LinkRelation.of("feed");

    private final FeedPublisher feedPublisher;

    FeedPublicationController(FeedPublisher feedPublisher) {
        this.feedPublisher = feedPublisher;
    }

    @PostMapping(ApiPaths.PODCAST_FEED_PUBLICATIONS)
    @Operation(
            operationId = "publishPodcastFeed",
            summary = "Write the feed of a podcast",
            description = "Writes the RSS feed of the podcast to the object storage from the current data, as is "
                    + "done automatically after each change of the podcast, its image or its episodes. Useful to "
                    + "list an episode scheduled in the future once its publication date has passed (the feed is a "
                    + "snapshot written on change), or after a failed write (503). No body. Answers 200, not 201: the "
                    + "publication is not an addressable resource, the feed URL is in the body (feedUrl, feed link).")
    @ApiResponse(responseCode = "200", description = "Feed written")
    @ApiResponse(
            responseCode = "503",
            description = "The object storage is unavailable: the feed was not written",
            content = @Content)
    ResponseEntity<EntityModel<FeedPublicationResponse>> publish(@PathVariable String slug) {
        FeedPublication publication = feedPublisher.publish(slug);
        return ResponseEntity.ok(EntityModel.of(
                FeedPublicationResponse.of(publication),
                ApiLinks.link(PODCAST, ApiPaths.PODCAST, slug),
                Link.of(publication.feedUrl().toString(), FEED)));
    }
}

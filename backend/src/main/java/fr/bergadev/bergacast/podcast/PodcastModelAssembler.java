package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.PublicUrls;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Converts podcasts to their HAL representation. */
@Component
class PodcastModelAssembler implements RepresentationModelAssembler<PodcastOverview, EntityModel<PodcastResponse>> {

    static final LinkRelation EPISODES = LinkRelation.of("episodes");
    static final LinkRelation API_TOKENS = LinkRelation.of("apiTokens");
    static final LinkRelation FEED = LinkRelation.of("feed");
    static final LinkRelation IMAGE = LinkRelation.of("image");
    static final LinkRelation FEED_PUBLICATIONS = LinkRelation.of("feedPublications");

    private final PublicUrls publicUrls;
    private final PodcastImageModelAssembler imageAssembler;

    PodcastModelAssembler(PublicUrls publicUrls, PodcastImageModelAssembler imageAssembler) {
        this.publicUrls = publicUrls;
        this.imageAssembler = imageAssembler;
    }

    @Override
    public EntityModel<PodcastResponse> toModel(PodcastOverview overview) {
        Podcast podcast = overview.podcast();
        String slug = podcast.getSlug();
        PodcastDetails details = podcast.getDetails();
        PodcastImageMetadata image = overview.image();
        PodcastResponse response = new PodcastResponse(
                podcast.getId(),
                slug,
                details.title(),
                details.description(),
                details.author(),
                details.ownerName(),
                details.ownerEmail(),
                details.language(),
                details.category(),
                details.explicit(),
                details.imageUrl(),
                image == null ? null : imageAssembler.toResponse(slug, image),
                details.websiteUrl(),
                podcast.getFeedSettings(),
                publicUrls.feed(slug),
                publicUrls.publish(slug),
                overview.episodeCount(),
                podcast.getCreatedAt(),
                podcast.getUpdatedAt());
        return EntityModel.of(
                response,
                ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.PODCAST, slug),
                ApiLinks.link(EPISODES, ApiPaths.EPISODES, slug),
                ApiLinks.link(API_TOKENS, ApiPaths.PODCAST_API_TOKENS, slug),
                Link.of(publicUrls.feed(slug).toString(), FEED),
                // Always present: the cover image resource, where the first image is uploaded too.
                ApiLinks.link(IMAGE, ApiPaths.PODCAST_IMAGE, slug),
                ApiLinks.link(FEED_PUBLICATIONS, ApiPaths.PODCAST_FEED_PUBLICATIONS, slug));
    }
}

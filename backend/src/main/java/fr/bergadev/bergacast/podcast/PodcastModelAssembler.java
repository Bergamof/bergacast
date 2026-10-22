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
class PodcastModelAssembler
        implements RepresentationModelAssembler<PodcastWithEpisodeCount, EntityModel<PodcastResponse>> {

    static final LinkRelation EPISODES = LinkRelation.of("episodes");
    static final LinkRelation API_TOKENS = LinkRelation.of("apiTokens");
    static final LinkRelation FEED = LinkRelation.of("feed");

    private final PublicUrls publicUrls;

    PodcastModelAssembler(PublicUrls publicUrls) {
        this.publicUrls = publicUrls;
    }

    @Override
    public EntityModel<PodcastResponse> toModel(PodcastWithEpisodeCount podcastWithCount) {
        Podcast podcast = podcastWithCount.podcast();
        String slug = podcast.getSlug();
        PodcastDetails details = podcast.getDetails();
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
                details.websiteUrl(),
                podcast.getFeedSettings(),
                publicUrls.feed(slug),
                publicUrls.publish(slug),
                podcastWithCount.episodeCount(),
                podcast.getCreatedAt(),
                podcast.getUpdatedAt());
        return EntityModel.of(
                response,
                ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.PODCAST, slug),
                ApiLinks.link(EPISODES, ApiPaths.EPISODES, slug),
                ApiLinks.link(API_TOKENS, ApiPaths.PODCAST_API_TOKENS, slug),
                Link.of(publicUrls.feed(slug).toString(), FEED));
    }
}

package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.PublicUrls;
import java.net.URI;
import java.util.UUID;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.stereotype.Component;

/** Converts episode cover images to their representation. */
@Component
class EpisodeImageModelAssembler {

    static final LinkRelation EPISODE = LinkRelation.of("episode");
    static final LinkRelation CONTENT = LinkRelation.of("content");

    private final PublicUrls publicUrls;

    EpisodeImageModelAssembler(PublicUrls publicUrls) {
        this.publicUrls = publicUrls;
    }

    EntityModel<EpisodeImageResponse> toModel(String slug, UUID episodeId, EpisodeImage image) {
        URI contentUrl = publicUrls.episodeImage(slug, episodeId, image.format());
        return EntityModel.of(
                EpisodeImageResponse.of(image, contentUrl),
                ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.EPISODE_IMAGE, slug, episodeId),
                ApiLinks.link(EPISODE, ApiPaths.EPISODE, slug, episodeId),
                Link.of(contentUrl.toString(), CONTENT));
    }
}

package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.PublicUrls;
import java.net.URI;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.stereotype.Component;

/** Converts podcast cover images to their representation. */
@Component
class PodcastImageModelAssembler {

    static final LinkRelation PODCAST = LinkRelation.of("podcast");
    static final LinkRelation CONTENT = LinkRelation.of("content");

    private final PublicUrls publicUrls;

    PodcastImageModelAssembler(PublicUrls publicUrls) {
        this.publicUrls = publicUrls;
    }

    PodcastImageResponse toResponse(String slug, PodcastImageMetadata image) {
        return PodcastImageResponse.of(image, contentUrl(slug, image));
    }

    EntityModel<PodcastImageResponse> toModel(String slug, PodcastImageMetadata image) {
        return EntityModel.of(
                toResponse(slug, image),
                ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.PODCAST_IMAGE, slug),
                ApiLinks.link(PODCAST, ApiPaths.PODCAST, slug),
                Link.of(contentUrl(slug, image).toString(), CONTENT));
    }

    private URI contentUrl(String slug, PodcastImageMetadata image) {
        return publicUrls.podcastImage(slug, image.format().fileExtension());
    }
}

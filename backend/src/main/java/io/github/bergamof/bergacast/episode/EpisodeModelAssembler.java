package io.github.bergamof.bergacast.episode;

import io.github.bergamof.bergacast.audio.AudioFile;
import io.github.bergamof.bergacast.web.ApiLinks;
import io.github.bergamof.bergacast.web.ApiPaths;
import io.github.bergamof.bergacast.web.PublicUrls;
import java.net.URI;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Converts episodes to their HAL representation. */
@Component
class EpisodeModelAssembler implements RepresentationModelAssembler<Episode, EntityModel<EpisodeResponse>> {

    static final LinkRelation PODCAST = LinkRelation.of("podcast");
    static final LinkRelation AUDIO = LinkRelation.of("audio");

    private final PublicUrls publicUrls;

    EpisodeModelAssembler(PublicUrls publicUrls) {
        this.publicUrls = publicUrls;
    }

    @Override
    public EntityModel<EpisodeResponse> toModel(Episode episode) {
        String slug = episode.getPodcast().getSlug();
        AudioFile audioFile = episode.getAudioFile();
        URI audioUrl = publicUrls.audio(slug, episode.getId(), audioFile.getFileExtension());
        EpisodeMetadata metadata = episode.metadata();
        EpisodeResponse response = new EpisodeResponse(
                episode.getId(),
                metadata.guid(),
                metadata.title(),
                metadata.description(),
                metadata.publicationDate(),
                episode.getAddedAt(),
                metadata.durationSeconds(),
                metadata.season(),
                metadata.episodeNumber(),
                metadata.episodeType(),
                metadata.explicit(),
                new EpisodeResponse.Audio(
                        audioFile.getMimeType(), audioFile.getSizeBytes(), audioFile.getSha256(), audioUrl));
        return EntityModel.of(
                response,
                ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.EPISODE, slug, episode.getId()),
                ApiLinks.link(PODCAST, ApiPaths.PODCAST, slug),
                Link.of(audioUrl.toString(), AUDIO));
    }
}

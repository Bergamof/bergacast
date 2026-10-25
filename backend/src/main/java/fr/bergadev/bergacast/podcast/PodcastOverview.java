package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.web.EntityTags;
import org.jspecify.annotations.Nullable;

/**
 * A podcast with the data derived from its related resources, the state exposed by the API.
 *
 * @param podcast the podcast
 * @param episodeCount number of episodes, including unpublished ones
 * @param image metadata of its uploaded cover image, if any
 */
public record PodcastOverview(
        Podcast podcast, long episodeCount, @Nullable PodcastImageMetadata image) {

    /**
     * Entity tag of the representation, which changes whenever the podcast (including its image, which bumps its
     * version) or its number of episodes changes.
     */
    public String entityTag() {
        return EntityTags.strong(podcast.getVersion(), episodeCount);
    }
}

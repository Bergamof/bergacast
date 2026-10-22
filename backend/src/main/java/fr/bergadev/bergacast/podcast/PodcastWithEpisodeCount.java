package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.web.EntityTags;

/**
 * A podcast and its number of episodes, the state exposed by the API.
 *
 * @param podcast the podcast
 * @param episodeCount number of episodes, including unpublished ones
 */
public record PodcastWithEpisodeCount(Podcast podcast, long episodeCount) {

    /** Entity tag of the representation, which changes whenever the podcast or its number of episodes changes. */
    public String entityTag() {
        return EntityTags.strong(podcast.getVersion(), episodeCount);
    }
}

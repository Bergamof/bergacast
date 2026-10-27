package fr.bergadev.bergacast.podcast;

/**
 * Event published, in the transaction of the change, when something the feed of a podcast shows changes: the podcast
 * itself, its image or its episodes. The feed is written again once the change is committed.
 *
 * @param podcastSlug slug of the podcast whose feed must be written again
 */
public record PodcastFeedChanged(String podcastSlug) {}

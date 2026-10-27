package fr.bergadev.bergacast.podcast;

/**
 * Event published, in the transaction of the deletion, when a podcast is deleted: its objects (feed, image, audio
 * files) are removed from the storage once the deletion is committed.
 *
 * @param podcastSlug slug of the deleted podcast
 */
public record PodcastDeleted(String podcastSlug) {}

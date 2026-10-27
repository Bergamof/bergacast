package fr.bergadev.bergacast.feed;

/**
 * RSS document of a podcast.
 *
 * @param document UTF-8 encoded RSS document
 * @param episodeCount number of episodes it lists
 */
record RenderedFeed(byte[] document, int episodeCount) {}

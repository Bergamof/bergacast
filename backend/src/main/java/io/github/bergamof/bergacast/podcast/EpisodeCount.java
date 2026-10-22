package io.github.bergamof.bergacast.podcast;

import java.util.UUID;

/**
 * Number of episodes of a podcast (query projection).
 *
 * @param podcastId podcast identifier
 * @param count number of episodes
 */
public record EpisodeCount(UUID podcastId, long count) {}

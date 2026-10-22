package fr.bergadev.bergacast.token;

import java.util.UUID;

/**
 * Identity carried by a valid API token.
 *
 * @param tokenId token identifier
 * @param podcastSlug slug of the only podcast the token gives access to
 */
public record AuthenticatedApiToken(UUID tokenId, String podcastSlug) {}

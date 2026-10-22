package fr.bergadev.bergacast.token;

/**
 * A newly created token, the only moment its plain value is known.
 *
 * @param apiToken stored token
 * @param plainToken plain value, to be shown once to the user
 */
public record CreatedApiToken(ApiToken apiToken, String plainToken) {}

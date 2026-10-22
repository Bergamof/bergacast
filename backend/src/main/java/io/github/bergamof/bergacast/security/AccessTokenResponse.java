package io.github.bergamof.bergacast.security;

/**
 * An admin access token, in the shape of an OAuth 2 token response (RFC 6749, section 5.1).
 *
 * @param accessToken signed JWT
 * @param tokenType always {@code Bearer}
 * @param expiresIn lifetime in seconds
 */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {}

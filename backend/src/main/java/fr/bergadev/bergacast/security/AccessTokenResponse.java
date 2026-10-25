package fr.bergadev.bergacast.security;

import io.swagger.v3.oas.annotations.media.Schema;

/** An admin access token, in the shape of an OAuth 2 token response (RFC 6749, section 5.1). */
@Schema(description = "Administrator access token, shaped like an OAuth 2 token response (RFC 6749, section 5.1).")
public record AccessTokenResponse(
        @Schema(
                description =
                        "Signed JWT, to send as `Authorization: Bearer <accessToken>` to the admin " + "operations.",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzZjJiOGMxZCJ9.c2lnbmF0dXJl")
        String accessToken,

        @Schema(description = "Type of the token, always Bearer.", example = "Bearer")
        String tokenType,

        @Schema(description = "Lifetime of the token in seconds: log in again once it has expired.", example = "900")
        long expiresIn) {}

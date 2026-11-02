package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator login. Answers 200 rather than 201 because the token is not an addressable resource, like the
 * OAuth 2 token endpoint.
 */
@RestController
@Tag(name = "Access tokens")
class AccessTokenController {

    private final AdminAccessTokenIssuer issuer;

    AccessTokenController(AdminAccessTokenIssuer issuer) {
        this.issuer = issuer;
    }

    @PostMapping(
            path = ApiPaths.ACCESS_TOKENS,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createAccessToken",
            summary = "Log in as an administrator",
            description = "Public. Exchanges the credentials of an administrator for a short-lived JWT, to send as "
                    + "`Authorization: Bearer <accessToken>` until it expires (expiresIn), then log in again. "
                    + "Answers 200 rather than 201: like an OAuth 2 token endpoint, the token is not a resource. "
                    + "After 3 consecutive failures for a username, its logins are refused with 429 for 10 seconds, "
                    + "a delay doubled at each further failure (up to one hour) until a successful login.")
    @ApiResponse(responseCode = "200", description = "Access token issued (never cached: Cache-Control no-store)")
    @ApiResponse(responseCode = "401", description = "Unknown username or wrong password", content = @Content)
    @ApiResponse(
            responseCode = "422",
            description = "Missing username or password, listed in errors",
            content = @Content)
    @ApiResponse(
            responseCode = "429",
            description = "Too many consecutive failed logins for this username: wait for Retry-After before trying "
                    + "again (the password is not checked meanwhile)",
            headers =
                    @Header(
                            name = "Retry-After",
                            description = "Seconds to wait before the next login attempt for this username",
                            schema = @Schema(type = "integer", example = "10")),
            content = @Content)
    ResponseEntity<AccessTokenResponse> create(@Valid @RequestBody AccessTokenRequest credentials) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issuer.issue(credentials));
    }
}

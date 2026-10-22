package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
@Tag(name = "Access tokens", description = "Administrator login")
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
            description = "Public. Exchanges credentials for a short-lived JWT to send as 'Authorization: Bearer'.")
    @ApiResponse(responseCode = "200", description = "Access token issued")
    @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    ResponseEntity<AccessTokenResponse> create(@Valid @RequestBody AccessTokenRequest credentials) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issuer.issue(credentials));
    }
}

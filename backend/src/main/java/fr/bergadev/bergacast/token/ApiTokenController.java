package fr.bergadev.bergacast.token;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.CollectionModels;
import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration of the API tokens of a podcast. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "API tokens", description = "Tokens allowing automated tools to publish episodes (admin only)")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class ApiTokenController {

    private final ApiTokenService apiTokenService;

    ApiTokenController(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @GetMapping(ApiPaths.PODCAST_API_TOKENS)
    @Operation(
            operationId = "listApiTokens",
            summary = "List the API tokens of a podcast",
            description = "Plain token values are never returned after their creation.")
    CollectionModel<EntityModel<ApiTokenResponse>> list(@PathVariable String slug) {
        List<EntityModel<ApiTokenResponse>> tokens = apiTokenService.list(slug).stream()
                .map(apiToken -> toModel(slug, apiToken, null))
                .toList();
        return CollectionModels.of(
                tokens,
                ApiTokenResponse.class,
                ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.PODCAST_API_TOKENS, slug));
    }

    @PostMapping(path = ApiPaths.PODCAST_API_TOKENS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createApiToken",
            summary = "Create an API token",
            description = "The plain token is only returned in this response: store it safely.")
    @ApiResponse(responseCode = "201", description = "Token created")
    @ApiResponse(responseCode = "422", description = "Invalid content", content = @Content)
    ResponseEntity<EntityModel<ApiTokenResponse>> create(
            @PathVariable String slug, @Valid @RequestBody CreateApiTokenRequest request) {
        CreatedApiToken created = apiTokenService.create(slug, request.name());
        ApiToken apiToken = created.apiToken();
        return ResponseEntity.created(ApiLinks.uri(ApiPaths.PODCAST_API_TOKEN, slug, apiToken.getId()))
                .body(toModel(slug, apiToken, created.plainToken()));
    }

    @DeleteMapping(ApiPaths.PODCAST_API_TOKEN)
    @Operation(
            operationId = "deleteApiToken",
            summary = "Revoke an API token",
            description = "The token stops working immediately.")
    @ApiResponse(responseCode = "204", description = "Token revoked")
    ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID tokenId) {
        apiTokenService.delete(slug, tokenId);
        return ResponseEntity.noContent().build();
    }

    private static EntityModel<ApiTokenResponse> toModel(String slug, ApiToken apiToken, @Nullable String plainToken) {
        ApiTokenResponse response = new ApiTokenResponse(
                apiToken.getId(),
                apiToken.getName(),
                apiToken.getTokenPrefix(),
                apiToken.getCreatedAt(),
                apiToken.getLastUsedAt(),
                plainToken);
        return EntityModel.of(
                response, ApiLinks.link(IanaLinkRelations.SELF, ApiPaths.PODCAST_API_TOKEN, slug, apiToken.getId()));
    }
}

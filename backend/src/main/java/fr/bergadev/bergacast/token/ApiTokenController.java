package fr.bergadev.bergacast.token;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.PagedModels;
import fr.bergadev.bergacast.web.SecuritySchemes;
import fr.bergadev.bergacast.web.SortableProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
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
@Tag(name = "API tokens")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class ApiTokenController {

    private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "name", "lastUsedAt");

    private final ApiTokenService apiTokenService;
    private final PagedResourcesAssembler<ApiToken> pagedAssembler;

    ApiTokenController(ApiTokenService apiTokenService, PagedResourcesAssembler<ApiToken> pagedAssembler) {
        this.apiTokenService = apiTokenService;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping(ApiPaths.PODCAST_API_TOKENS)
    @Operation(
            operationId = "listApiTokens",
            summary = "List the API tokens of a podcast",
            description = "Lists the API tokens of the podcast, oldest first by default, with their prefix and last "
                    + "use; sortable by createdAt, name or lastUsedAt. Plain token values are never returned after "
                    + "their creation.")
    PagedModel<EntityModel<ApiTokenResponse>> list(
            @PathVariable String slug, @ParameterObject @PageableDefault(sort = "createdAt") Pageable pageable) {
        return PagedModels.of(
                apiTokenService.list(slug, SortableProperties.restrict(pageable, SORTABLE_PROPERTIES)),
                pagedAssembler,
                apiToken -> toModel(slug, apiToken, null),
                ApiTokenResponse.class);
    }

    @PostMapping(path = ApiPaths.PODCAST_API_TOKENS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createApiToken",
            summary = "Create an API token",
            description = "Creates a long-lived token allowing an automated tool to list, read and publish the "
                    + "episodes of this podcast, sent as `Authorization: Bearer bgc_…`. The plain token is only "
                    + "returned in this response (token member): store it safely. Only its SHA-256 hash is kept.")
    @ApiResponse(responseCode = "201", description = "Token created, with its plain value")
    @ApiResponse(responseCode = "422", description = "Invalid content, listed in errors", content = @Content)
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
            description = "Deletes the token: requests using it are rejected with 401 immediately.")
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

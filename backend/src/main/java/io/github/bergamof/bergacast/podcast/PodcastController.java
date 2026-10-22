package io.github.bergamof.bergacast.podcast;

import io.github.bergamof.bergacast.web.ApiLinks;
import io.github.bergamof.bergacast.web.ApiMediaTypes;
import io.github.bergamof.bergacast.web.ApiPaths;
import io.github.bergamof.bergacast.web.PagedModels;
import io.github.bergamof.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Administration of podcasts. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Podcasts", description = "Podcast administration (admin only)")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class PodcastController {

    private final PodcastService podcastService;
    private final PodcastModelAssembler assembler;
    private final PagedResourcesAssembler<PodcastWithEpisodeCount> pagedAssembler;

    PodcastController(
            PodcastService podcastService,
            PodcastModelAssembler assembler,
            PagedResourcesAssembler<PodcastWithEpisodeCount> pagedAssembler) {
        this.podcastService = podcastService;
        this.assembler = assembler;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping(ApiPaths.PODCASTS)
    @Operation(operationId = "listPodcasts", summary = "List podcasts", description = "Sortable by slug, createdAt.")
    PagedModel<EntityModel<PodcastResponse>> list(@ParameterObject @PageableDefault(sort = "slug") Pageable pageable) {
        return PagedModels.of(podcastService.list(pageable), pagedAssembler, assembler, PodcastResponse.class);
    }

    @PostMapping(path = ApiPaths.PODCASTS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createPodcast",
            summary = "Create a podcast",
            description = "The slug is immutable; missing feed settings take their default values.")
    @ApiResponse(responseCode = "201", description = "Podcast created")
    @ApiResponse(responseCode = "409", description = "Slug already used", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid content", content = @Content)
    ResponseEntity<EntityModel<PodcastResponse>> create(@Valid @RequestBody PodcastInput input) {
        PodcastWithEpisodeCount created = podcastService.create(input);
        return ResponseEntity.created(
                        ApiLinks.uri(ApiPaths.PODCAST, created.podcast().getSlug()))
                .eTag(created.entityTag())
                .body(assembler.toModel(created));
    }

    @GetMapping(ApiPaths.PODCAST)
    @Operation(operationId = "getPodcast", summary = "Get a podcast", description = "Supports If-None-Match.")
    @ApiResponse(responseCode = "200", description = "The podcast")
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    ResponseEntity<EntityModel<PodcastResponse>> get(@PathVariable String slug) {
        PodcastWithEpisodeCount podcast = podcastService.get(slug);
        return ResponseEntity.ok().eTag(podcast.entityTag()).body(assembler.toModel(podcast));
    }

    @PatchMapping(path = ApiPaths.PODCAST, consumes = ApiMediaTypes.MERGE_PATCH_JSON_VALUE)
    @Operation(
            operationId = "updatePodcast",
            summary = "Update a podcast",
            description = "JSON Merge Patch (RFC 7396). The slug cannot be changed. Supports If-Match.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = ApiMediaTypes.MERGE_PATCH_JSON_VALUE,
                                            schema = @Schema(implementation = PodcastInput.class))))
    @ApiResponse(responseCode = "200", description = "Podcast updated")
    @ApiResponse(responseCode = "412", description = "If-Match does not match", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid content", content = @Content)
    ResponseEntity<EntityModel<PodcastResponse>> update(
            @PathVariable String slug,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestBody JsonNode patch) {
        PodcastWithEpisodeCount updated = podcastService.patch(slug, patch, ifMatch);
        return ResponseEntity.ok().eTag(updated.entityTag()).body(assembler.toModel(updated));
    }

    @DeleteMapping(ApiPaths.PODCAST)
    @Operation(
            operationId = "deletePodcast",
            summary = "Delete a podcast",
            description = "Also deletes its episodes, their audio content and its API tokens.")
    @ApiResponse(responseCode = "204", description = "Podcast deleted")
    ResponseEntity<Void> delete(@PathVariable String slug) {
        podcastService.delete(slug);
        return ResponseEntity.noContent().build();
    }
}

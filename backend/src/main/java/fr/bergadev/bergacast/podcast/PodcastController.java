package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiMediaTypes;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.EntityTagged;
import fr.bergadev.bergacast.web.PagedModels;
import fr.bergadev.bergacast.web.SecuritySchemes;
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
@Tag(name = "Podcasts")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class PodcastController {

    private final PodcastService podcastService;
    private final PodcastModelAssembler assembler;
    private final PagedResourcesAssembler<PodcastOverview> pagedAssembler;

    PodcastController(
            PodcastService podcastService,
            PodcastModelAssembler assembler,
            PagedResourcesAssembler<PodcastOverview> pagedAssembler) {
        this.podcastService = podcastService;
        this.assembler = assembler;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping(ApiPaths.PODCASTS)
    @Operation(
            operationId = "listPodcasts",
            summary = "List podcasts",
            description = "Lists the podcasts with their episode count. Sorted by slug by default; sortable by slug, "
                    + "createdAt or updatedAt.")
    PagedModel<EntityModel<PodcastResponse>> list(@ParameterObject @PageableDefault(sort = "slug") Pageable pageable) {
        return PagedModels.of(podcastService.list(pageable), pagedAssembler, assembler, PodcastResponse.class);
    }

    @PostMapping(path = ApiPaths.PODCASTS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createPodcast",
            summary = "Create a podcast",
            description = "Creates a podcast, whose RSS feed is immediately available at feedUrl (empty until the "
                    + "first episode). The slug cannot be changed afterwards. Absent optional fields take their "
                    + "default: explicit false, all feed settings as documented in FeedSettingsInput.")
    @ApiResponse(responseCode = "201", description = "Podcast created")
    @ApiResponse(responseCode = "409", description = "The slug is already used by another podcast", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid content, listed in errors", content = @Content)
    @EntityTagged
    ResponseEntity<EntityModel<PodcastResponse>> create(@Valid @RequestBody PodcastInput input) {
        PodcastOverview created = podcastService.create(input);
        return ResponseEntity.created(
                        ApiLinks.uri(ApiPaths.PODCAST, created.podcast().getSlug()))
                .eTag(created.entityTag())
                .body(assembler.toModel(created));
    }

    @GetMapping(ApiPaths.PODCAST)
    @Operation(
            operationId = "getPodcast",
            summary = "Get a podcast",
            description = "Returns the podcast with its feed settings, public URLs and episode count. Supports "
                    + "conditional requests with If-None-Match.")
    @ApiResponse(responseCode = "200", description = "The podcast")
    @ApiResponse(
            responseCode = "304",
            description = "Not modified since the ETag sent in If-None-Match",
            content = @Content)
    @EntityTagged
    ResponseEntity<EntityModel<PodcastResponse>> get(@PathVariable String slug) {
        PodcastOverview podcast = podcastService.get(slug);
        return ResponseEntity.ok().eTag(podcast.entityTag()).body(assembler.toModel(podcast));
    }

    @PatchMapping(path = ApiPaths.PODCAST, consumes = ApiMediaTypes.MERGE_PATCH_JSON_VALUE)
    @Operation(
            operationId = "updatePodcast",
            summary = "Update a podcast",
            description = """
                    JSON Merge Patch (RFC 7396) of the podcast: an absent member is left unchanged, a `null` \
                    member is cleared, and `feed` is merged member by member.

                    Rules differ slightly from a creation: `slug` cannot be changed (it may be sent unchanged); \
                    `title`, `description` and `language` cannot be set to `null`; `explicit` set to `null` \
                    becomes false; `feed` set to `null`, or one of its members, resets it to its default.

                    Send the ETag in `If-Match` to avoid overwriting a concurrent change.""",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = ApiMediaTypes.MERGE_PATCH_JSON_VALUE,
                                            schema = @Schema(implementation = PodcastInput.class))))
    @ApiResponse(responseCode = "200", description = "Podcast updated")
    @ApiResponse(
            responseCode = "412",
            description = "If-Match does not match the current ETag: the podcast has changed",
            content = @Content)
    @ApiResponse(
            responseCode = "422",
            description = "Invalid patched content (including a changed slug), listed in errors",
            content = @Content)
    @EntityTagged
    ResponseEntity<EntityModel<PodcastResponse>> update(
            @PathVariable String slug,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestBody JsonNode patch) {
        PodcastOverview updated = podcastService.patch(slug, patch, ifMatch);
        return ResponseEntity.ok().eTag(updated.entityTag()).body(assembler.toModel(updated));
    }

    @DeleteMapping(ApiPaths.PODCAST)
    @Operation(
            operationId = "deletePodcast",
            summary = "Delete a podcast",
            description = "Deletes the podcast with its episodes, their audio files and its API tokens: its feed and "
                    + "audio URLs stop working. Cannot be undone.")
    @ApiResponse(responseCode = "204", description = "Podcast deleted")
    ResponseEntity<Void> delete(@PathVariable String slug) {
        podcastService.delete(slug);
        return ResponseEntity.noContent().build();
    }
}

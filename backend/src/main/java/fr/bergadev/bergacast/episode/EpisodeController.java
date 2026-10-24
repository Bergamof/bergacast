package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.web.ApiHeaders;
import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiMediaTypes;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.PagedModels;
import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Part;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

/** Publication (podcast API token or admin) and administration (admin) of episodes. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Episodes", description = "Episode publication and administration")
class EpisodeController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private static final String METADATA_PART = "metadata";
    private static final String AUDIO_PART = "audio";

    private final EpisodeService episodeService;
    private final EpisodePublication episodePublication;
    private final EpisodeMetadataReader metadataReader;
    private final EpisodeModelAssembler assembler;
    private final PagedResourcesAssembler<Episode> pagedAssembler;

    EpisodeController(
            EpisodeService episodeService,
            EpisodePublication episodePublication,
            EpisodeMetadataReader metadataReader,
            EpisodeModelAssembler assembler,
            PagedResourcesAssembler<Episode> pagedAssembler) {
        this.episodeService = episodeService;
        this.episodePublication = episodePublication;
        this.metadataReader = metadataReader;
        this.assembler = assembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping(path = ApiPaths.EPISODES_MAPPING, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "publishEpisode",
            summary = "Publish an episode",
            description = "Uploads the audio file and the metadata of a new episode. With an Idempotency-Key "
                    + "already used for this podcast, returns the episode created by the first request. The metadata "
                    + "part should be sent as application/json; for convenience, a part without content type or "
                    + "sent as text/plain or application/octet-stream is also parsed as JSON.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                            schema = @Schema(implementation = EpisodeUploadForm.class),
                                            encoding = {
                                                @Encoding(
                                                        name = METADATA_PART,
                                                        contentType = MediaType.APPLICATION_JSON_VALUE),
                                                @Encoding(name = AUDIO_PART, contentType = "audio/*")
                                            })),
            security = {
                @SecurityRequirement(name = SecuritySchemes.PODCAST_TOKEN),
                @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
            })
    @ApiResponse(responseCode = "201", description = "Episode published (or already published with this key)")
    @ApiResponse(responseCode = "409", description = "Duplicate guid", content = @Content)
    @ApiResponse(responseCode = "413", description = "Audio file too large", content = @Content)
    @ApiResponse(responseCode = "400", description = "The metadata part is not JSON", content = @Content)
    @ApiResponse(
            responseCode = "415",
            description = "The audio part is not audio/*, or the metadata part cannot hold JSON",
            content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid metadata", content = @Content)
    ResponseEntity<EntityModel<EpisodeResponse>> publish(
            @PathVariable String slug,
            @Parameter(hidden = true) @RequestPart(METADATA_PART) Part metadata,
            @Parameter(hidden = true) @RequestPart(AUDIO_PART) MultipartFile audio,
            @Parameter(description = "Makes retries safe: a key already used for this podcast replays the result")
                    @RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false)
                    @Size(max = MAX_IDEMPOTENCY_KEY_LENGTH)
                    @Nullable
                    String idempotencyKey) {
        Episode episode = episodePublication.publish(slug, metadataReader.read(metadata), audio, idempotencyKey);
        return ResponseEntity.created(ApiLinks.uri(ApiPaths.EPISODE, slug, episode.getId()))
                .eTag(EpisodeService.entityTag(episode))
                .body(assembler.toModel(episode));
    }

    @GetMapping(ApiPaths.EPISODES_MAPPING)
    @Operation(
            operationId = "listEpisodes",
            summary = "List the episodes of a podcast",
            description = "Includes unpublished episodes. Sorted by publicationDate descending by default.",
            security = {
                @SecurityRequirement(name = SecuritySchemes.PODCAST_TOKEN),
                @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
            })
    PagedModel<EntityModel<EpisodeResponse>> list(
            @PathVariable String slug,
            @ParameterObject @PageableDefault(sort = "publicationDate", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return PagedModels.of(episodeService.list(slug, pageable), pagedAssembler, assembler, EpisodeResponse.class);
    }

    @GetMapping(ApiPaths.EPISODE_MAPPING)
    @Operation(
            operationId = "getEpisode",
            summary = "Get an episode",
            description = "Supports If-None-Match.",
            security = {
                @SecurityRequirement(name = SecuritySchemes.PODCAST_TOKEN),
                @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
            })
    @ApiResponse(responseCode = "200", description = "The episode")
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    ResponseEntity<EntityModel<EpisodeResponse>> get(@PathVariable String slug, @PathVariable UUID episodeId) {
        Episode episode = episodeService.get(slug, episodeId);
        return ResponseEntity.ok().eTag(EpisodeService.entityTag(episode)).body(assembler.toModel(episode));
    }

    @PatchMapping(path = ApiPaths.EPISODE_MAPPING, consumes = ApiMediaTypes.MERGE_PATCH_JSON_VALUE)
    @Operation(
            operationId = "updateEpisode",
            summary = "Update the metadata of an episode",
            description = "JSON Merge Patch (RFC 7396). The audio file cannot be replaced. Supports If-Match.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = ApiMediaTypes.MERGE_PATCH_JSON_VALUE,
                                            schema = @Schema(implementation = EpisodeInput.class))),
            security = @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT))
    @ApiResponse(responseCode = "200", description = "Episode updated")
    @ApiResponse(responseCode = "409", description = "Duplicate guid", content = @Content)
    @ApiResponse(responseCode = "412", description = "If-Match does not match", content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid content", content = @Content)
    ResponseEntity<EntityModel<EpisodeResponse>> update(
            @PathVariable String slug,
            @PathVariable UUID episodeId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
            @RequestBody JsonNode patch) {
        Episode episode = episodeService.patch(slug, episodeId, patch, ifMatch);
        return ResponseEntity.ok().eTag(EpisodeService.entityTag(episode)).body(assembler.toModel(episode));
    }

    @DeleteMapping(ApiPaths.EPISODE_MAPPING)
    @Operation(
            operationId = "deleteEpisode",
            summary = "Delete an episode",
            description = "Also deletes its audio content.",
            security = @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT))
    @ApiResponse(responseCode = "204", description = "Episode deleted")
    ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID episodeId) {
        episodeService.delete(slug, episodeId);
        return ResponseEntity.noContent().build();
    }
}

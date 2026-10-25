package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.web.ApiHeaders;
import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiMediaTypes;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.EntityTagged;
import fr.bergadev.bergacast.web.PagedModels;
import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@Tag(name = "Episodes")
class EpisodeController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private static final String METADATA_PART = "metadata";
    private static final String AUDIO_PART = "audio";
    private static final String PUBLISH_DESCRIPTION = """
            Publishes a new episode of the podcast: the audio file and its metadata are stored, and the episode \
            appears in the public feed (`/{slug}.xml`) from its `publicationDate`.

            **Authentication**: an API token of this podcast (`Authorization: Bearer bgc_…`) or an administrator JWT.

            **Body** (`multipart/form-data`), two parts:
            - `metadata`: JSON object where only `title` is required. Defaults: `guid` is a random UUID, \
            `publicationDate` the upload time, `episodeType` FULL.
            - `audio`: the audio file, with a concrete `audio/*` content type (e.g. `audio/mpeg`), at most \
            BERGACAST_MAX_UPLOAD_SIZE (500MB by default).

            **Scheduling**: with a `publicationDate` in the future, the episode stays out of the feed until that \
            date, unless the podcast feed settings include future episodes.

            **Retries**: send an `Idempotency-Key` header. A request reusing a key already used for this podcast \
            publishes nothing and returns `201` with the episode published by the first request (its audio part \
            is ignored), so a tool can safely retry after a timeout.

            **Example**, where `episode-42.json` contains \
            `{"title": "Episode 42", "publicationDate": "2026-11-02T06:00:00+01:00"}`:
            ```sh
            curl https://podcasts.example.com/api/v1/my-podcast/episodes \\
              -H 'Authorization: Bearer bgc_…' \\
              -H 'Idempotency-Key: my-podcast-042' \\
              -F 'metadata=<episode-42.json;type=application/json' \\
              -F 'audio=@episode-42.mp3;type=audio/mpeg'
            ```

            **Metadata content type**: the `metadata` part should be sent as `application/json`. For tools that \
            cannot set it (n8n, Zapier, HTML forms…), a part without content type, or sent as `text/plain`, \
            `application/octet-stream` or `application/*+json`, is also parsed as JSON.""";
    private static final String SCHEDULED_EPISODE_EXAMPLE = """
            {
              "metadata": {
                "title": "Episode 42: Self-hosting a podcast",
                "description": "We set up a podcast server at home and discuss RSS feeds.",
                "publicationDate": "2026-11-02T06:00:00+01:00",
                "guid": "my-podcast-042",
                "durationSeconds": 2712,
                "season": 3,
                "episodeNumber": 42,
                "episodeType": "FULL",
                "explicit": false
              },
              "audio": "(binary content of episode-42.mp3, sent as audio/mpeg)"
            }""";
    private static final String MINIMAL_EPISODE_EXAMPLE = """
            {
              "metadata": {"title": "Episode 42: Self-hosting a podcast"},
              "audio": "(binary content of episode-42.mp3, sent as audio/mpeg)"
            }""";

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
            description = PUBLISH_DESCRIPTION,
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "The episode metadata (JSON) and its audio file.",
                            content =
                                    @Content(
                                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                            schema = @Schema(implementation = EpisodeUploadForm.class),
                                            encoding = {
                                                @Encoding(
                                                        name = METADATA_PART,
                                                        contentType = MediaType.APPLICATION_JSON_VALUE),
                                                @Encoding(name = AUDIO_PART, contentType = "audio/*")
                                            },
                                            examples = {
                                                @ExampleObject(
                                                        name = "scheduled",
                                                        summary = "Episode scheduled for a future date",
                                                        description = "All the metadata, with a publicationDate in "
                                                                + "the future.",
                                                        value = SCHEDULED_EPISODE_EXAMPLE),
                                                @ExampleObject(
                                                        name = "minimal",
                                                        summary = "Title only, published immediately",
                                                        description = "Every other member takes its default.",
                                                        value = MINIMAL_EPISODE_EXAMPLE)
                                            })),
            security = {
                @SecurityRequirement(name = SecuritySchemes.PODCAST_TOKEN),
                @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
            })
    @ApiResponse(
            responseCode = "201",
            description = "Episode published, or the episode already published with this Idempotency-Key")
    @ApiResponse(
            responseCode = "400",
            description = "The metadata part is not a JSON object with values of the right types",
            content = @Content)
    @ApiResponse(responseCode = "409", description = "Another episode of the podcast has this guid", content = @Content)
    @ApiResponse(
            responseCode = "413",
            description = "The audio file exceeds the maximum upload size (BERGACAST_MAX_UPLOAD_SIZE)",
            content = @Content)
    @ApiResponse(
            responseCode = "415",
            description = "The audio part has no concrete audio/* content type, or the metadata part has a content "
                    + "type that cannot hold JSON",
            content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid metadata, listed in errors", content = @Content)
    @EntityTagged
    ResponseEntity<EntityModel<EpisodeResponse>> publish(
            @PathVariable String slug,
            @Parameter(hidden = true) @RequestPart(METADATA_PART) Part metadata,
            @Parameter(hidden = true) @RequestPart(AUDIO_PART) MultipartFile audio,
            @Parameter(
                            description = "Unique key of this publication, e.g. a UUID or the episode's own "
                                    + "identifier, making retries safe: a key already used for this podcast returns "
                                    + "the episode published with it instead of publishing again.",
                            example = "my-podcast-042")
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
            description = "Lists all the episodes of the podcast, including those not in the feed yet (scheduled) or "
                    + "no longer in it (beyond maxEpisodes). Sorted by publicationDate, newest first, by default; "
                    + "sortable by any episode property, e.g. publicationDate, addedAt, title, episodeNumber.",
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
            description = "Returns the episode, published or not, with the public URL of its audio file. Supports "
                    + "conditional requests with If-None-Match.",
            security = {
                @SecurityRequirement(name = SecuritySchemes.PODCAST_TOKEN),
                @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
            })
    @ApiResponse(responseCode = "200", description = "The episode")
    @ApiResponse(
            responseCode = "304",
            description = "Not modified since the ETag sent in If-None-Match",
            content = @Content)
    @EntityTagged
    ResponseEntity<EntityModel<EpisodeResponse>> get(@PathVariable String slug, @PathVariable UUID episodeId) {
        Episode episode = episodeService.get(slug, episodeId);
        return ResponseEntity.ok().eTag(EpisodeService.entityTag(episode)).body(assembler.toModel(episode));
    }

    @PatchMapping(path = ApiPaths.EPISODE_MAPPING, consumes = ApiMediaTypes.MERGE_PATCH_JSON_VALUE)
    @Operation(
            operationId = "updateEpisode",
            summary = "Update the metadata of an episode",
            description = """
                    JSON Merge Patch (RFC 7396) of the episode metadata: an absent member is left unchanged, a \
                    `null` member is cleared. The audio file cannot be replaced (delete the episode and publish \
                    it again).

                    Rules differ slightly from a publication: `title`, `guid`, `publicationDate` and \
                    `episodeType` cannot be set to `null` (no default is applied); the other members can. Changing \
                    `guid` makes podcast apps see a new episode. Moving `publicationDate` to the future removes \
                    the episode from the feed until then (unless the feed includes future episodes).

                    Send the ETag in `If-Match` to avoid overwriting a concurrent change.""",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = ApiMediaTypes.MERGE_PATCH_JSON_VALUE,
                                            schema = @Schema(implementation = EpisodeInput.class))),
            security = @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT))
    @ApiResponse(responseCode = "200", description = "Episode updated")
    @ApiResponse(responseCode = "409", description = "Another episode of the podcast has this guid", content = @Content)
    @ApiResponse(
            responseCode = "412",
            description = "If-Match does not match the current ETag: the episode has changed",
            content = @Content)
    @ApiResponse(responseCode = "422", description = "Invalid patched metadata, listed in errors", content = @Content)
    @EntityTagged
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
            description = "Deletes the episode and its audio file: it disappears from the feed and its audio URL "
                    + "stops working.",
            security = @SecurityRequirement(name = SecuritySchemes.ADMIN_JWT))
    @ApiResponse(responseCode = "204", description = "Episode deleted")
    ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID episodeId) {
        episodeService.delete(slug, episodeId);
        return ResponseEntity.noContent().build();
    }
}

package fr.bergadev.bergacast.episode;

import fr.bergadev.bergacast.config.BergacastProperties;
import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.ImageUploads;
import fr.bergadev.bergacast.web.LimitedRequestBody;
import fr.bergadev.bergacast.web.SecuritySchemes;
import fr.bergadev.bergacast.web.StorageResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration of the optional cover image of an episode, a singleton sub-resource of the episode. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Episodes")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class EpisodeImageController {

    private static final String ETAG_DESCRIPTION = "Strong entity tag of the image: its SHA-256, quoted";

    private final EpisodeImageService imageService;
    private final EpisodeImageModelAssembler assembler;
    private final DataSize maxUploadSize;

    EpisodeImageController(
            EpisodeImageService imageService, EpisodeImageModelAssembler assembler, BergacastProperties properties) {
        this.imageService = imageService;
        this.assembler = assembler;
        this.maxUploadSize = properties.maxImageUploadSize();
    }

    @PutMapping(
            path = ApiPaths.EPISODE_IMAGE_MAPPING,
            consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    @Operation(
            operationId = "putEpisodeImage",
            summary = "Upload the cover image of an episode",
            description = "Creates or replaces the optional cover image of the episode with the raw request body, a "
                    + "JPEG or PNG image whose Content-Type matches its actual format. Images larger than 3000 "
                    + "pixels on their largest side are downscaled to 3000 pixels, aspect ratio preserved, and "
                    + "re-encoded in the same format; smaller ones are stored as is. Images of more than 50 "
                    + "megapixels are rejected. The body may not exceed BERGACAST_MAX_IMAGE_UPLOAD_SIZE (20 MB by "
                    + "default). The feed item then carries it as its itunes:image. Supports If-Match; an unknown "
                    + "episode (404) or a stale If-Match (412) is answered before the body is read. Changes the "
                    + "episode, hence its ETag. An image can also be sent with the publication of the episode.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "The image file, sent as is (not multipart)",
                            content = {
                                @Content(
                                        mediaType = MediaType.IMAGE_JPEG_VALUE,
                                        schema = @Schema(type = "string", format = "binary")),
                                @Content(
                                        mediaType = MediaType.IMAGE_PNG_VALUE,
                                        schema = @Schema(type = "string", format = "binary"))
                            }))
    @ApiResponse(
            responseCode = "200",
            description = "Image replaced",
            headers =
                    @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION, schema = @Schema(type = "string")))
    @ApiResponse(
            responseCode = "201",
            description = "Image created",
            headers = {
                @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION, schema = @Schema(type = "string")),
                @Header(
                        name = HttpHeaders.LOCATION,
                        description = "URL of the image resource",
                        schema = @Schema(type = "string", format = "uri"))
            })
    @ApiResponse(responseCode = "412", description = "If-Match does not match the current image", content = @Content)
    @ApiResponse(responseCode = "413", description = "Image larger than the upload limit", content = @Content)
    @ApiResponse(
            responseCode = "415",
            description = "Content-Type is neither image/jpeg nor image/png",
            content = @Content)
    @ApiResponse(
            responseCode = "422",
            description = "Not a valid image of the declared type, or more than 50 megapixels",
            content = @Content)
    @ApiResponse(
            responseCode = StorageResponses.UNAVAILABLE,
            description = StorageResponses.UPLOAD_OR_FEED_FAILED,
            content = @Content)
    ResponseEntity<EntityModel<EpisodeImageResponse>> put(
            @PathVariable String slug,
            @PathVariable UUID episodeId,
            @Parameter(description = "Only replace the image if its current ETag is one of these")
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    @Nullable
                    String ifMatch,
            HttpServletRequest request)
            throws IOException {
        ImageFormat format = ImageUploads.declaredFormat(request.getContentType(), "The image");
        // 404 and 412 are answered before reading a body of up to the upload limit.
        imageService.prepare(slug, episodeId, ifMatch);
        byte[] content = LimitedRequestBody.read(request, maxUploadSize);
        EpisodeImageService.EpisodeImageChange change = imageService.put(slug, episodeId, content, format, ifMatch);
        EntityModel<EpisodeImageResponse> body = assembler.toModel(slug, episodeId, change.image());
        ResponseEntity.BodyBuilder response = change.created()
                ? ResponseEntity.created(ApiLinks.uri(ApiPaths.EPISODE_IMAGE, slug, episodeId))
                : ResponseEntity.ok();
        return response.eTag(change.image().entityTag()).body(body);
    }

    @GetMapping(ApiPaths.EPISODE_IMAGE_MAPPING)
    @Operation(
            operationId = "getEpisodeImage",
            summary = "Get the cover image metadata of an episode",
            description = "Dimensions, size, checksum and public URL of the image; the content itself is served "
                    + "publicly at the URL of the content link. Supports If-None-Match.")
    @ApiResponse(
            responseCode = "200",
            description = "The image metadata",
            headers =
                    @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION, schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such episode, or it has no image", content = @Content)
    ResponseEntity<EntityModel<EpisodeImageResponse>> get(@PathVariable String slug, @PathVariable UUID episodeId) {
        EpisodeImage image = imageService.get(slug, episodeId);
        return ResponseEntity.ok().eTag(image.entityTag()).body(assembler.toModel(slug, episodeId, image));
    }

    @DeleteMapping(ApiPaths.EPISODE_IMAGE_MAPPING)
    @Operation(
            operationId = "deleteEpisodeImage",
            summary = "Delete the cover image of an episode",
            description = "The feed item then carries no image of its own: podcast apps show the image of the "
                    + "podcast. Changes the episode, hence its ETag.")
    @ApiResponse(responseCode = "204", description = "Image deleted")
    @ApiResponse(responseCode = "404", description = "No such episode, or it has no image", content = @Content)
    @ApiResponse(
            responseCode = StorageResponses.UNAVAILABLE,
            description = StorageResponses.FEED_NOT_WRITTEN,
            content = @Content)
    ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID episodeId) {
        imageService.delete(slug, episodeId);
        return ResponseEntity.noContent().build();
    }
}

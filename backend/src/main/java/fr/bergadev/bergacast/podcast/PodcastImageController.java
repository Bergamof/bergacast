package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.config.BergacastProperties;
import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.web.ApiLinks;
import fr.bergadev.bergacast.web.ApiPaths;
import fr.bergadev.bergacast.web.LimitedRequestBody;
import fr.bergadev.bergacast.web.SecuritySchemes;
import fr.bergadev.bergacast.web.UnsupportedMediaTypeException;
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

/** Administration of the cover image of a podcast, a singleton sub-resource of the podcast. */
@RestController
@RequestMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Podcasts")
@SecurityRequirement(name = SecuritySchemes.ADMIN_JWT)
class PodcastImageController {

    private static final String SLUG_DESCRIPTION = "Slug of the podcast";
    private static final String ETAG_DESCRIPTION = "Strong entity tag of the image: its SHA-256, quoted";

    private final PodcastImageService imageService;
    private final PodcastImageModelAssembler assembler;
    private final DataSize maxUploadSize;

    PodcastImageController(
            PodcastImageService imageService, PodcastImageModelAssembler assembler, BergacastProperties properties) {
        this.imageService = imageService;
        this.assembler = assembler;
        this.maxUploadSize = properties.maxImageUploadSize();
    }

    @PutMapping(
            path = ApiPaths.PODCAST_IMAGE,
            consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    @Operation(
            operationId = "putPodcastImage",
            summary = "Upload the cover image of a podcast",
            description = "Creates or replaces the cover image with the raw request body, a JPEG or PNG image whose "
                    + "Content-Type matches its actual format. Images larger than 3000 pixels (the Apple Podcasts "
                    + "maximum) on their largest side are downscaled to 3000 pixels, aspect ratio preserved, and "
                    + "re-encoded in the same format; smaller ones are stored as is. Images of more than 50 "
                    + "megapixels are rejected. The body may not exceed BERGACAST_MAX_IMAGE_UPLOAD_SIZE (20 MB by "
                    + "default). Supports If-Match; an unknown podcast (404) or a stale If-Match (412) is answered "
                    + "before the body is read. Changes the podcast, hence its ETag.",
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
    @ApiResponse(responseCode = "404", description = "No such podcast", content = @Content)
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
    ResponseEntity<EntityModel<PodcastImageResponse>> put(
            @Parameter(description = SLUG_DESCRIPTION) @PathVariable String slug,
            @Parameter(description = "Only replace the image if its current ETag is one of these")
                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                    @Nullable
                    String ifMatch,
            HttpServletRequest request)
            throws IOException {
        ImageFormat format = declaredFormat(request);
        // 404 and 412 are answered before reading a body of up to the upload limit.
        imageService.prepare(slug, ifMatch);
        byte[] content = LimitedRequestBody.read(request, maxUploadSize);
        PodcastImageService.PodcastImageChange change = imageService.put(slug, content, format, ifMatch);
        EntityModel<PodcastImageResponse> body = assembler.toModel(slug, change.image());
        ResponseEntity.BodyBuilder response = change.created()
                ? ResponseEntity.created(ApiLinks.uri(ApiPaths.PODCAST_IMAGE, slug))
                : ResponseEntity.ok();
        return response.eTag(change.image().entityTag()).body(body);
    }

    @GetMapping(ApiPaths.PODCAST_IMAGE)
    @Operation(
            operationId = "getPodcastImage",
            summary = "Get the cover image metadata of a podcast",
            description = "Dimensions, size, checksum and public URL of the uploaded image; the content itself is "
                    + "served publicly at the URL of the content link. Supports If-None-Match.")
    @ApiResponse(
            responseCode = "200",
            description = "The image metadata",
            headers =
                    @Header(name = HttpHeaders.ETAG, description = ETAG_DESCRIPTION, schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such podcast, or it has no image", content = @Content)
    ResponseEntity<EntityModel<PodcastImageResponse>> get(
            @Parameter(description = SLUG_DESCRIPTION) @PathVariable String slug) {
        PodcastImageMetadata image = imageService.get(slug);
        return ResponseEntity.ok().eTag(image.entityTag()).body(assembler.toModel(slug, image));
    }

    @DeleteMapping(ApiPaths.PODCAST_IMAGE)
    @Operation(
            operationId = "deletePodcastImage",
            summary = "Delete the cover image of a podcast",
            description = "The feed then falls back to the external imageUrl of the podcast, if any. Changes the "
                    + "podcast, hence its ETag.")
    @ApiResponse(responseCode = "204", description = "Image deleted")
    @ApiResponse(responseCode = "404", description = "No such podcast, or it has no image", content = @Content)
    ResponseEntity<Void> delete(@Parameter(description = SLUG_DESCRIPTION) @PathVariable String slug) {
        imageService.delete(slug);
        return ResponseEntity.noContent().build();
    }

    /** The mapping only accepts the supported types; parameters such as {@code charset} are ignored. */
    private static ImageFormat declaredFormat(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            throw unsupported();
        }
        MediaType mediaType = MediaType.parseMediaType(contentType);
        return ImageFormat.fromMimeType(mediaType.getType() + "/" + mediaType.getSubtype())
                .orElseThrow(PodcastImageController::unsupported);
    }

    private static UnsupportedMediaTypeException unsupported() {
        return new UnsupportedMediaTypeException("The image must be sent as image/jpeg or image/png");
    }
}

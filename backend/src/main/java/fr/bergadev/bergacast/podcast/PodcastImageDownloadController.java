package fr.bergadev.bergacast.podcast;

import fr.bergadev.bergacast.web.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Public download of podcast cover images, linked from the feeds. The URL stays the same when the image is replaced:
 * clients revalidate it with its entity tag.
 */
@RestController
@Tag(name = "Public feed")
class PodcastImageDownloadController {

    static final String IMAGE_PATH = "/{slug}/image.{extension}";
    /** Same freshness as the feed, which links the image. */
    private static final CacheControl FIVE_MINUTES =
            CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final PodcastImageService imageService;

    PodcastImageDownloadController(PodcastImageService imageService) {
        this.imageService = imageService;
    }

    @RequestMapping(
            path = IMAGE_PATH,
            method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(
            operationId = "downloadPodcastImage",
            summary = "Download the cover image of a podcast",
            description = "Public. Serves the uploaded image; the extension must match its format (jpg for JPEG, png "
                    + "for PNG). Supports If-None-Match.")
    @Parameter(
            name = "slug",
            in = ParameterIn.PATH,
            required = true,
            description = "Slug of the podcast",
            schema = @Schema(type = "string"))
    @Parameter(
            name = "extension",
            in = ParameterIn.PATH,
            required = true,
            description = "File extension of the image format",
            schema =
                    @Schema(
                            type = "string",
                            allowableValues = {"jpg", "png"}))
    @ApiResponse(
            responseCode = "200",
            description = "The image",
            headers =
                    @Header(
                            name = HttpHeaders.ETAG,
                            description = "Strong entity tag of the image: its SHA-256, quoted",
                            schema = @Schema(type = "string")),
            content = {
                @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))
            })
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "No such podcast, no image, or an extension not matching its format",
            content = @Content)
    void download(
            @PathVariable String slug,
            @PathVariable String extension,
            ServletWebRequest webRequest,
            HttpServletResponse response)
            throws IOException {
        PodcastImageMetadata image =
                imageService.find(slug).orElseThrow(() -> new ResourceNotFoundException("No image for '" + slug + "'"));
        if (!image.format().fileExtension().equals(extension)) {
            throw new ResourceNotFoundException("The image of '" + slug + "' is served as image."
                    + image.format().fileExtension());
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, FIVE_MINUTES.getHeaderValue());
        if (webRequest.checkNotModified(image.entityTag())) {
            return;
        }
        // HEAD answers from the metadata; GET reads the content before writing any header, so that an image deleted
        // meanwhile still gets a clean 404.
        boolean head = HttpMethod.HEAD.matches(webRequest.getRequest().getMethod());
        byte[] content = head ? null : imageService.content(slug, image);
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(image.mimeType());
        response.setContentLengthLong(image.sizeBytes());
        if (content != null) {
            response.getOutputStream().write(content);
        }
    }
}

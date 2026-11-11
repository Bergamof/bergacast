package fr.bergadev.bergacast.web;

import fr.bergadev.bergacast.image.ImageFormat;
import org.jspecify.annotations.Nullable;

/** Reads the format an upload declares for a cover image. */
public final class ImageUploads {

    private ImageUploads() {}

    /**
     * Returns the format announced by a {@code Content-Type} header value, parameters such as {@code charset}
     * ignored.
     *
     * @param subject what carries the content type, e.g. {@code "The image"} or {@code "The image part"}
     * @throws UnsupportedMediaTypeException when the content type is not {@code image/jpeg} or {@code image/png}
     */
    public static ImageFormat declaredFormat(@Nullable String contentType, String subject) {
        return ImageFormat.fromContentType(contentType)
                .orElseThrow(() -> new UnsupportedMediaTypeException(
                        subject + " must have an image/jpeg or image/png content type"));
    }
}

package fr.bergadev.bergacast.image;

/**
 * An image ready to be stored: the uploaded bytes untouched, or a downscaled re-encoding of them.
 *
 * @param content encoded image
 * @param format format of {@code content}
 * @param width width in pixels
 * @param height height in pixels
 */
public record ProcessedImage(byte[] content, ImageFormat format, int width, int height) {}

package fr.bergadev.bergacast.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Validates uploaded images and downscales the ones larger than a maximum size.
 *
 * <p>An image is accepted when its actual format is the declared one, its header announces at most
 * {@link #MAX_PIXELS} pixels (checked before decoding, against decompression bombs) and it decodes completely. Images
 * whose largest side fits in the maximum are kept byte for byte; larger ones are downscaled, aspect ratio preserved,
 * by successive halvings (bilinear, which amounts to box filtering) then a last bicubic step, and re-encoded in the
 * same format.
 *
 * <p>Memory and temporary files: everything happens in memory ({@code MemoryCache*} streams, no {@code ImageIO}
 * cache file).
 */
@Component
public class ImageResizer {

    /** Largest side accepted by Apple Podcasts for cover art. */
    public static final int APPLE_PODCASTS_MAX_SIDE = 3000;

    /** Largest decoded image accepted, 50 megapixels (about 200 MB once decoded in ARGB). */
    public static final long MAX_PIXELS = 50_000_000L;

    static final float JPEG_QUALITY = 0.9f;
    private static final int FIRST_IMAGE = 0;
    private static final int HALVING_DIVISOR = 2;

    private final int maxSide;
    private final long maxPixels;

    public ImageResizer() {
        this(APPLE_PODCASTS_MAX_SIDE, MAX_PIXELS);
    }

    ImageResizer(int maxSide, long maxPixels) {
        this.maxSide = maxSide;
        this.maxPixels = maxPixels;
    }

    /**
     * Checks an uploaded image and makes it fit in a square of {@code maxSide} pixels.
     *
     * @param content encoded image as uploaded
     * @param declaredFormat format announced by the client ({@code Content-Type})
     * @throws InvalidImageException when the content is not a valid image of the declared format, or is too large
     */
    public ProcessedImage fit(byte[] content, ImageFormat declaredFormat) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidImageException("is not a readable " + declaredFormat.name() + " image");
            }
            ImageReader reader = readers.next();
            try {
                requireFormat(reader, declaredFormat);
                reader.setInput(input, true, true);
                return fit(content, declaredFormat, reader);
            } finally {
                reader.dispose();
            }
        } catch (IOException readingFailure) {
            throw new InvalidImageException("is not a readable " + declaredFormat.name() + " image", readingFailure);
        }
    }

    private ProcessedImage fit(byte[] content, ImageFormat format, ImageReader reader) {
        BufferedImage decoded = decode(reader, format);
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        if (Math.max(width, height) <= maxSide) {
            return new ProcessedImage(content, format, width, height);
        }
        BufferedImage scaled = downscale(decoded, format);
        return new ProcessedImage(encode(scaled, format), format, scaled.getWidth(), scaled.getHeight());
    }

    private static void requireFormat(ImageReader reader, ImageFormat declaredFormat) throws IOException {
        ImageFormat actualFormat = ImageFormat.fromImageIoName(reader.getFormatName())
                .orElseThrow(() -> new InvalidImageException("is neither a JPEG nor a PNG image"));
        if (actualFormat != declaredFormat) {
            throw new InvalidImageException(
                    "is a " + actualFormat.name() + " image but was declared as " + declaredFormat.mimeType());
        }
    }

    /** Reads the dimensions from the header first, so that oversized images are rejected before allocating them. */
    private BufferedImage decode(ImageReader reader, ImageFormat format) {
        try {
            long pixels = (long) reader.getWidth(FIRST_IMAGE) * reader.getHeight(FIRST_IMAGE);
            if (pixels > maxPixels) {
                throw new InvalidImageException("has " + pixels + " pixels, more than the " + maxPixels + " allowed");
            }
            return reader.read(FIRST_IMAGE);
        } catch (IOException | RuntimeException decodingFailure) {
            if (decodingFailure instanceof InvalidImageException invalid) {
                throw invalid;
            }
            // Decoders report malformed data with IIOException but also with assorted runtime exceptions.
            throw new InvalidImageException("is not a valid " + format.name() + " image", decodingFailure);
        }
    }

    private BufferedImage downscale(BufferedImage source, ImageFormat format) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int targetWidth;
        int targetHeight;
        if (sourceWidth >= sourceHeight) {
            targetWidth = maxSide;
            targetHeight = proportional(sourceHeight, sourceWidth);
        } else {
            targetHeight = maxSide;
            targetWidth = proportional(sourceWidth, sourceHeight);
        }
        int imageType = keepsAlpha(source, format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = source;
        int width = sourceWidth;
        int height = sourceHeight;
        while (width / HALVING_DIVISOR >= targetWidth && height / HALVING_DIVISOR >= targetHeight) {
            width /= HALVING_DIVISOR;
            height /= HALVING_DIVISOR;
            current = draw(current, width, height, imageType, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, targetWidth, targetHeight, imageType, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    /** The other side of the downscaled image, rounded, never zero. */
    private int proportional(int side, int largestSide) {
        return Math.max(1, Math.round((float) side * maxSide / largestSide));
    }

    /** JPEG has no alpha channel; PNGs keep theirs (including palette transparency). */
    private static boolean keepsAlpha(BufferedImage image, ImageFormat format) {
        return format == ImageFormat.PNG && image.getColorModel().getTransparency() != Transparency.OPAQUE;
    }

    private static BufferedImage draw(
            BufferedImage source, int width, int height, int imageType, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, imageType);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image, ImageFormat format) {
        ImageWriter writer =
                ImageIO.getImageWritersByFormatName(format.imageIoName()).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), writeParam(writer, format));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot encode a " + format.name() + " image in memory", exception);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static ImageWriteParam writeParam(ImageWriter writer, ImageFormat format) {
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (format == ImageFormat.JPEG) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
        }
        return param;
    }
}

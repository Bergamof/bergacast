package fr.bergadev.bergacast.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageResizerTest {

    private final ImageResizer resizer = new ImageResizer();

    @Test
    void keepsTheOriginalBytesOfAnImageWithinTheMaximumSize() throws IOException {
        byte[] jpeg = TestImages.jpeg(3000, 1200);

        ProcessedImage processed = resizer.fit(jpeg, ImageFormat.JPEG);

        assertThat(processed.content()).isSameAs(jpeg);
        assertThat(processed.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(processed.width()).isEqualTo(3000);
        assertThat(processed.height()).isEqualTo(1200);
    }

    @Test
    void downscalesALandscapeJpegToTheMaximumWidthKeepingItsAspectRatio() throws IOException {
        byte[] jpeg = TestImages.jpeg(4000, 2000);

        ProcessedImage processed = resizer.fit(jpeg, ImageFormat.JPEG);

        assertThat(processed.width()).isEqualTo(3000);
        assertThat(processed.height()).isEqualTo(1500);
        BufferedImage stored = TestImages.decode(processed.content());
        assertThat(TestImages.formatOf(processed.content())).isEqualToIgnoringCase("jpeg");
        assertThat(stored.getWidth()).isEqualTo(3000);
        assertThat(stored.getHeight()).isEqualTo(1500);
    }

    @Test
    void downscalesAPortraitPngToTheMaximumHeightKeepingItsAspectRatio() throws IOException {
        byte[] png = TestImages.png(1000, 7000);

        ProcessedImage processed = resizer.fit(png, ImageFormat.PNG);

        assertThat(processed.width()).isEqualTo(429);
        assertThat(processed.height()).isEqualTo(3000);
        assertThat(TestImages.formatOf(processed.content())).isEqualToIgnoringCase("png");
        BufferedImage stored = TestImages.decode(processed.content());
        assertThat(stored.getWidth()).isEqualTo(429);
        assertThat(stored.getHeight()).isEqualTo(3000);
        assertThat(stored.getColorModel().hasAlpha()).isFalse();
    }

    @Test
    void preservesTheColoursOfADownscaledImage() throws IOException {
        BufferedImage red = new BufferedImage(6000, 6000, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = red.createGraphics();
        graphics.setColor(new Color(200, 30, 40));
        graphics.fillRect(0, 0, 6000, 6000);
        graphics.dispose();

        ProcessedImage processed = resizer.fit(TestImages.encode(red, "png"), ImageFormat.PNG);

        BufferedImage stored = TestImages.decode(processed.content());
        assertThat(processed.width()).isEqualTo(3000);
        assertThat(new Color(stored.getRGB(1500, 1500))).isEqualTo(new Color(200, 30, 40));
    }

    @Test
    void keepsTheTransparencyOfADownscaledPng() throws IOException {
        BufferedImage translucent = new BufferedImage(3200, 3200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = translucent.createGraphics();
        graphics.setColor(new Color(0, 0, 255, 255));
        graphics.fillRect(0, 0, 1600, 3200);
        graphics.dispose();

        ProcessedImage processed = resizer.fit(TestImages.encode(translucent, "png"), ImageFormat.PNG);

        BufferedImage stored = TestImages.decode(processed.content());
        assertThat(stored.getColorModel().hasAlpha()).isTrue();
        assertThat(stored.getRGB(100, 1500) >>> 24).isEqualTo(255);
        assertThat(stored.getRGB(2900, 1500) >>> 24).isZero();
    }

    @Test
    void rejectsAnImageWhoseActualFormatIsNotTheDeclaredOne() throws IOException {
        byte[] png = TestImages.png(10, 10);

        assertThatThrownBy(() -> resizer.fit(png, ImageFormat.JPEG))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("is a PNG image but was declared as image/jpeg");
    }

    @Test
    void rejectsAnImageOfAnotherFormat() throws IOException {
        byte[] gif = TestImages.encode(TestImages.gradient(10, 10, BufferedImage.TYPE_INT_RGB), "gif");

        assertThatThrownBy(() -> resizer.fit(gif, ImageFormat.PNG))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("is neither a JPEG nor a PNG image");
    }

    @Test
    void rejectsBytesThatAreNotAnImage() {
        byte[] text = "definitely not an image".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> resizer.fit(text, ImageFormat.PNG))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("is not a readable PNG image");
        assertThatThrownBy(() -> resizer.fit(new byte[0], ImageFormat.JPEG))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("is not a readable JPEG image");
    }

    @Test
    void rejectsATruncatedImage() throws IOException {
        byte[] png = TestImages.png(400, 400);
        byte[] truncated = Arrays.copyOf(png, png.length / 2);

        assertThatThrownBy(() -> resizer.fit(truncated, ImageFormat.PNG))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("is not a valid PNG image");
    }

    @Test
    void rejectsAnImageAnnouncingTooManyPixelsWithoutDecodingIt() {
        // 10000 x 10000 announced in the header, with no actual pixel data: decoding would fail, the header check
        // must reject it first.
        byte[] bomb = TestImages.pngHeaderOnly(10_000, 10_000);

        assertThatThrownBy(() -> resizer.fit(bomb, ImageFormat.PNG))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("has 100000000 pixels, more than the 50000000 allowed");
    }

    @Test
    void appliesConfigurableLimits() throws IOException {
        ImageResizer small = new ImageResizer(100, 40_000);
        byte[] png = TestImages.png(200, 100);

        assertThat(small.fit(png, ImageFormat.PNG).width()).isEqualTo(100);
        byte[] tooLarge = TestImages.png(201, 200);
        assertThatThrownBy(() -> small.fit(tooLarge, ImageFormat.PNG)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void downscalesAGrayscaleJpegAsAColourJpeg() throws IOException {
        byte[] gray = TestImages.encode(TestImages.gradient(3300, 3300, BufferedImage.TYPE_BYTE_GRAY), "jpeg");

        ProcessedImage processed = resizer.fit(gray, ImageFormat.JPEG);

        assertThat(processed.width()).isEqualTo(3000);
        assertThat(processed.height()).isEqualTo(3000);
        assertThat(ImageIO.read(new ByteArrayInputStream(processed.content()))).isNotNull();
    }
}

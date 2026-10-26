package fr.bergadev.bergacast.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Images generated for tests, so that no binary fixture is needed. */
public final class TestImages {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private TestImages() {}

    /** An image with a diagonal gradient, which compresses poorly enough to look like a real picture. */
    public static BufferedImage gradient(int width, int height, int imageType) {
        BufferedImage image = new BufferedImage(width, height, imageType);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = x * 255 / width;
                int green = y * 255 / height;
                int blue = (x + y) % 256;
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return image;
    }

    public static byte[] png(int width, int height) {
        return encode(gradient(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(gradient(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    public static byte[] encode(BufferedImage image, String imageIoFormat) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, imageIoFormat, bytes)) {
                throw new IllegalArgumentException("No ImageIO writer for " + imageIoFormat);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    public static BufferedImage decode(byte[] content) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(content));
    }

    public static String formatOf(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            try {
                return reader.getFormatName();
            } finally {
                reader.dispose();
            }
        }
    }

    /** A PNG made of a valid header announcing the given dimensions, without any pixel data: a decompression bomb. */
    public static byte[] pngHeaderOnly(int width, int height) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream png = new DataOutputStream(bytes)) {
            png.write(PNG_SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream headerData = new DataOutputStream(header);
            headerData.writeInt(width);
            headerData.writeInt(height);
            headerData.writeByte(8); // bit depth
            headerData.writeByte(0); // grayscale
            headerData.writeByte(0); // deflate
            headerData.writeByte(0); // adaptive filtering
            headerData.writeByte(0); // no interlace
            writeChunk(png, "IHDR", header.toByteArray());
            writeChunk(png, "IEND", new byte[0]);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    private static void writeChunk(DataOutputStream png, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        png.writeInt(data.length);
        png.write(typeBytes);
        png.write(data);
        png.writeInt((int) crc.getValue());
    }
}

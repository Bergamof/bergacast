package fr.bergadev.bergacast.image;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Image formats accepted for cover art, the ones Apple Podcasts supports. */
public enum ImageFormat {
    JPEG("image/jpeg", "jpg", "jpeg"),
    PNG("image/png", "png", "png");

    private final String mimeType;
    private final String fileExtension;
    private final String imageIoName;

    ImageFormat(String mimeType, String fileExtension, String imageIoName) {
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.imageIoName = imageIoName;
    }

    /** Media type, e.g. {@code image/jpeg}. */
    public String mimeType() {
        return mimeType;
    }

    /** File extension used in public URLs, without the dot. */
    public String fileExtension() {
        return fileExtension;
    }

    String imageIoName() {
        return imageIoName;
    }

    /** Returns the format of a media type (type and subtype only, case-insensitive), if supported. */
    public static Optional<ImageFormat> fromMimeType(String mimeType) {
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> format.mimeType.equals(normalized))
                .findFirst();
    }

    static Optional<ImageFormat> fromImageIoName(String formatName) {
        return Arrays.stream(values())
                .filter(format -> format.imageIoName.equalsIgnoreCase(formatName))
                .findFirst();
    }
}

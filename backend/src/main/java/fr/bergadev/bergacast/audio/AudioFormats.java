package fr.bergadev.bergacast.audio;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

/** Chooses the file extension of the public audio URL, which some podcast apps rely on to detect the format. */
final class AudioFormats {

    static final String DEFAULT_EXTENSION = "bin";
    private static final Pattern SAFE_EXTENSION = Pattern.compile("^[a-z0-9]{1,10}$");
    private static final Map<String, String> EXTENSIONS_BY_SUBTYPE = Map.ofEntries(
            Map.entry("mpeg", "mp3"),
            Map.entry("mp3", "mp3"),
            Map.entry("mp4", "m4a"),
            Map.entry("x-m4a", "m4a"),
            Map.entry("aac", "aac"),
            Map.entry("ogg", "ogg"),
            Map.entry("opus", "opus"),
            Map.entry("wav", "wav"),
            Map.entry("x-wav", "wav"),
            Map.entry("flac", "flac"),
            Map.entry("x-flac", "flac"),
            Map.entry("webm", "webm"));

    private AudioFormats() {}

    /** Returns the extension of a well-known audio type, else the one of the uploaded file name if it is safe. */
    static String extensionFor(MediaType audioType, @Nullable String originalFilename) {
        String known = EXTENSIONS_BY_SUBTYPE.get(audioType.getSubtype().toLowerCase(Locale.ROOT));
        if (known != null) {
            return known;
        }
        return extensionOf(originalFilename);
    }

    private static String extensionOf(@Nullable String filename) {
        if (filename == null) {
            return DEFAULT_EXTENSION;
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SAFE_EXTENSION.matcher(extension).matches() ? extension : DEFAULT_EXTENSION;
    }
}

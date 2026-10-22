package fr.bergadev.bergacast.audio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

class AudioFormatsTest {

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource(
            nullValues = "NULL",
            value = {
                "audio/mpeg, x.bin, mp3",
                "audio/MP4, NULL, m4a",
                "audio/x-m4a, x, m4a",
                "audio/ogg, a.ogg, ogg",
                "audio/x-wav, NULL, wav",
                "audio/flac, NULL, flac",
                "audio/x-custom, show.MKA, mka",
                "audio/x-custom, NULL, bin",
                "audio/x-custom, no-extension, bin",
                "audio/x-custom, evil.../../x, bin"
            })
    void choosesTheFileExtension(String mimeType, String filename, String expected) {
        assertThat(AudioFormats.extensionFor(MediaType.parseMediaType(mimeType), filename))
                .isEqualTo(expected);
    }
}

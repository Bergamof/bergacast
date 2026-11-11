package fr.bergadev.bergacast.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImageFormatTest {

    @Test
    void readsTheFormatOfAContentTypeIgnoringCaseAndParameters() {
        assertThat(ImageFormat.fromContentType("image/jpeg")).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.fromContentType("IMAGE/PNG; charset=utf-8")).contains(ImageFormat.PNG);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"image/gif", "text/plain", "application/octet-stream", "not a media type", ""})
    void ignoresAContentTypeThatIsNotASupportedImage(String contentType) {
        assertThat(ImageFormat.fromContentType(contentType)).isEmpty();
    }
}

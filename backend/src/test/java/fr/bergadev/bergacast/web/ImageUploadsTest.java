package fr.bergadev.bergacast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.bergadev.bergacast.image.ImageFormat;
import org.junit.jupiter.api.Test;

class ImageUploadsTest {

    @Test
    void readsTheDeclaredFormat() {
        assertThat(ImageUploads.declaredFormat("image/png; charset=utf-8", "The image"))
                .isEqualTo(ImageFormat.PNG);
    }

    @Test
    void namesTheSubjectWhenTheTypeIsNotSupported() {
        assertThatThrownBy(() -> ImageUploads.declaredFormat("image/gif", "The image part"))
                .isInstanceOf(UnsupportedMediaTypeException.class)
                .hasMessage("The image part must have an image/jpeg or image/png content type");
        assertThatThrownBy(() -> ImageUploads.declaredFormat(null, "The image"))
                .isInstanceOf(UnsupportedMediaTypeException.class);
    }
}

package fr.bergadev.bergacast.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import fr.bergadev.bergacast.image.ImageFormat;
import fr.bergadev.bergacast.image.ImageResizer;
import fr.bergadev.bergacast.image.ProcessedImage;
import fr.bergadev.bergacast.image.TestImages;
import fr.bergadev.bergacast.storage.ObjectStorage;
import fr.bergadev.bergacast.web.ContentTooLargeException;
import fr.bergadev.bergacast.web.InvalidField;
import fr.bergadev.bergacast.web.Sha256;
import fr.bergadev.bergacast.web.UnprocessableContentException;
import fr.bergadev.bergacast.web.UnsupportedMediaTypeException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class EpisodeImagesTest {

    private static final String SLUG = "show";
    private static final UUID EPISODE_ID = UUID.fromString("3f2c8a1e-5b7d-4c9e-8f10-0123456789ab");
    private static final DataSize LIMIT = DataSize.ofMegabytes(1);

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final EpisodeImages images = new EpisodeImages(new ImageResizer(), storage);

    @Test
    void readsAnImagePart() {
        byte[] png = TestImages.png(300, 200);

        ProcessedImage image = images.fit(part("image/png", png), "image", LIMIT);

        assertThat(image.format()).isEqualTo(ImageFormat.PNG);
        assertThat(image.width()).isEqualTo(300);
        assertThat(image.content()).isEqualTo(png);
    }

    @Test
    void rejectsAPartThatIsNotAJpegOrPng() {
        assertThatThrownBy(() -> images.fit(part("image/gif", new byte[] {1}), "image", LIMIT))
                .isInstanceOf(UnsupportedMediaTypeException.class)
                .hasMessageContaining("image part");
        assertThatThrownBy(() -> images.fit(part(null, new byte[] {1}), "image", LIMIT))
                .isInstanceOf(UnsupportedMediaTypeException.class);
    }

    @Test
    void rejectsAPartLargerThanTheLimitWithoutDecodingIt() {
        byte[] tooLarge = new byte[(int) LIMIT.toBytes() + 1];

        assertThatThrownBy(() -> images.fit(part("image/png", tooLarge), "image", LIMIT))
                .isInstanceOf(ContentTooLargeException.class);
    }

    @Test
    void reportsTheFieldOfAnInvalidImage() {
        assertThatThrownBy(() -> images.fit(part("image/png", new byte[] {1, 2, 3}), "image", LIMIT))
                .isInstanceOfSatisfying(
                        UnprocessableContentException.class,
                        invalid -> assertThat(invalid.getBody().getProperties())
                                .containsEntry(
                                        UnprocessableContentException.ERRORS_PROPERTY,
                                        List.of(new InvalidField("image", "is not a readable PNG image"))));
        assertThatThrownBy(() -> images.fit(TestImages.png(10, 10), ImageFormat.JPEG, "$"))
                .isInstanceOf(UnprocessableContentException.class);
    }

    @Test
    void writesTheImageNextToTheAudioOfItsEpisode() {
        byte[] jpeg = TestImages.jpeg(640, 640);
        ProcessedImage image = new ProcessedImage(jpeg, ImageFormat.JPEG, 640, 640);

        EpisodeImage stored = images.store(SLUG, EPISODE_ID, image);

        verify(storage)
                .put(
                        eq("show/episodes/3f2c8a1e-5b7d-4c9e-8f10-0123456789ab/image.jpg"),
                        eq(jpeg),
                        eq("image/jpeg"),
                        any(CacheControl.class));
        assertThat(stored).isEqualTo(new EpisodeImage("image/jpeg", 640, 640, jpeg.length, Sha256.hex(jpeg)));
        assertThat(stored.entityTag()).isEqualTo("\"" + Sha256.hex(jpeg) + "\"");
        assertThat(stored.format()).isEqualTo(ImageFormat.JPEG);
    }

    @Test
    void removesTheObjectOfAnImageNoEpisodeReferences() {
        images.discard(SLUG, EPISODE_ID, ImageFormat.PNG);

        verify(storage).discard("show/episodes/3f2c8a1e-5b7d-4c9e-8f10-0123456789ab/image.png");
    }

    @Test
    void writesNothingWhenOnlyChecking() {
        images.fit(part("image/png", TestImages.png(20, 20)), "image", LIMIT);

        verifyNoInteractions(storage);
    }

    private static MockMultipartFile part(String contentType, byte[] content) {
        return new MockMultipartFile("image", "cover", contentType, content);
    }
}

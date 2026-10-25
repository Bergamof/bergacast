package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class ImageProcessingHintsTest {

    private final RuntimeHints hints = new RuntimeHints();

    @BeforeEach
    void registerHints() {
        new ImageProcessingHints().registerHints(hints, getClass().getClassLoader());
    }

    @Test
    void registersTheJdkTypesThatTheAwtNativeLibrariesAccessThroughJni() {
        assertThat(ImageProcessingHints.JNI_ACCESSED_TYPES)
                .contains("com.sun.imageio.plugins.jpeg.JPEGImageReader", "sun.java2d.loops.Blit")
                .doesNotHaveDuplicates()
                .allSatisfy(type -> assertThat(Class.forName(type)).isNotNull());
        assertThat(hints.jni().getTypeHint(BufferedImage.class))
                .isNotNull()
                .satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS));
        assertThat(hints.jni().getTypeHint(TypeReference.of("sun.java2d.SunGraphics2D")))
                .isNotNull();
    }

    @Test
    void registersOnlyTheMembersUsedToLoadTheHeadlessToolkit() throws NoSuchMethodException {
        assertThat(hints.jni().getTypeHint(GraphicsEnvironment.class).methods())
                .singleElement()
                .satisfies(method -> assertThat(method.getName()).isEqualTo("isHeadless"));
        assertThat(hints.jni().getTypeHint(System.class).methods())
                .singleElement()
                .satisfies(method -> assertThat(method.getName()).isEqualTo("load"));
        assertThat(hints.jni().getTypeHint(Boolean.class).getMemberCategories()).isEmpty();
    }

    @Test
    void registersTheRasterizerAndTheColourProfiles() {
        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(TypeReference.of("sun.java2d.marlin.DMarlinRenderingEngine"))
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("sun/java2d/cmm/profiles/sRGB.pf"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forBundle("sun.awt.resources.awt"))
                .accepts(hints);
    }
}

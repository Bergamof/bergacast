package fr.bergadev.bergacast.config;

import fr.bergadev.bergacast.adminuser.Password;
import fr.bergadev.bergacast.episode.EpisodeInput;
import fr.bergadev.bergacast.podcast.NotReservedSlug;
import fr.bergadev.bergacast.web.InvalidField;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Reflection needed by the GraalVM native image that Spring AOT and the libraries' own metadata do not register. The
 * entries come from the native-image tracing agent run with {@code backend/scripts/smoke-test.sh}, minus what Spring
 * AOT already generates. The JNI metadata of image processing (AWT, ImageIO) is in {@link ImageProcessingHints}.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints({NativeImageHintsConfiguration.Hints.class, ImageProcessingHints.class})
class NativeImageHintsConfiguration {

    static class Hints implements RuntimeHintsRegistrar {

        private final BindingReflectionHintsRegistrar bindingHints = new BindingReflectionHintsRegistrar();

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            // Hibernate multi-id loaders allocate arrays of the identifier type reflectively (entity ids are UUIDs).
            hints.reflection().registerType(UUID[].class);
            // Hibernate Validator instantiates the validators of custom constraints and reads their attributes.
            hints.reflection()
                    .registerType(NotReservedSlug.class, MemberCategory.INVOKE_DECLARED_METHODS)
                    .registerType(NotReservedSlug.Validator.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                    .registerType(Password.class, MemberCategory.INVOKE_DECLARED_METHODS)
                    .registerType(Password.Validator.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            // JSON bound outside of controller signatures: the episode upload metadata part (read by hand) and the
            // field errors added to Problem Details properties.
            bindingHints.registerReflectionHints(hints.reflection(), EpisodeInput.class, InvalidField.class);
        }
    }
}

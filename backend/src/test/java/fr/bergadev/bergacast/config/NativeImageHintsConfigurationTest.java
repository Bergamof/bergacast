package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.adminuser.Password;
import fr.bergadev.bergacast.episode.EpisodeInput;
import fr.bergadev.bergacast.episode.EpisodeType;
import fr.bergadev.bergacast.podcast.NotReservedSlug;
import fr.bergadev.bergacast.web.InvalidField;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.ReflectionHintsPredicates;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class NativeImageHintsConfigurationTest {

    private final RuntimeHints hints = new RuntimeHints();
    private final ReflectionHintsPredicates reflection = RuntimeHintsPredicates.reflection();

    @BeforeEach
    void registerHints() {
        new NativeImageHintsConfiguration.Hints()
                .registerHints(hints, getClass().getClassLoader());
    }

    @Test
    void registersTheIdentifierArrayTypeForHibernateMultiIdLoaders() {
        assertThat(reflection.onType(UUID[].class)).accepts(hints);
    }

    @Test
    void letsHibernateValidatorInstantiateCustomValidatorsAndReadTheirConstraints() {
        assertThat(reflection
                        .onType(NotReservedSlug.Validator.class)
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
        assertThat(reflection
                        .onType(Password.Validator.class)
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
        assertThat(reflection.onType(NotReservedSlug.class)).accepts(hints);
        assertThat(reflection.onType(Password.class)).accepts(hints);
    }

    @Test
    void registersJsonBindingOfTypesUsedOutsideOfControllerSignatures() throws NoSuchMethodException {
        assertThat(reflection.onMethodInvocation(EpisodeInput.class.getMethod("title")))
                .accepts(hints);
        assertThat(reflection.onType(EpisodeType.class)).accepts(hints);
        assertThat(reflection.onMethodInvocation(InvalidField.class.getMethod("field")))
                .accepts(hints);
    }
}

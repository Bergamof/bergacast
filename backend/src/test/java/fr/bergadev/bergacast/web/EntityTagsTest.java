package fr.bergadev.bergacast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EntityTagsTest {

    private static final String CURRENT = EntityTags.strong(3, 7);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"\"3.7\"", "*", "\"1.0\", \"3.7\"", " \"3.7\" "})
    void acceptsMissingOrMatchingIfMatch(String ifMatch) {
        assertThatCode(() -> EntityTags.requireIfMatch(ifMatch, CURRENT)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"3.6\"", "W/\"3.7\"", "3.7"})
    void rejectsOtherOrWeakTags(String ifMatch) {
        assertThatThrownBy(() -> EntityTags.requireIfMatch(ifMatch, CURRENT))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x"})
    void buildsQuotedStrongTags(String part) {
        assertThat(EntityTags.strong(part)).isEqualTo("\"x\"");
        assertThat(CURRENT).isEqualTo("\"3.7\"");
    }

    @Test
    void checksIfMatchAgainstAResourceThatMayNotExist() {
        assertThatCode(() -> EntityTags.requireIfMatch("\"3.7\"", Optional.of(CURRENT)))
                .doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.requireIfMatch(null, Optional.empty())).doesNotThrowAnyException();
        assertThatThrownBy(() -> EntityTags.requireIfMatch("\"3.6\"", Optional.of(CURRENT)))
                .isInstanceOf(PreconditionFailedException.class);
        assertThatThrownBy(() -> EntityTags.requireIfMatch("*", Optional.empty()))
                .isInstanceOf(PreconditionFailedException.class);
    }
}

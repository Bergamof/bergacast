package io.github.bergamof.bergacast.feed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XmlCharactersTest {

    @Test
    void keepsValidText() {
        String text = "Tab\tLF\nCR\r é 😀  �";

        assertThat(XmlCharacters.sanitize(text)).isSameAs(text);
    }

    @Test
    void removesCharactersForbiddenByXml() {
        assertThat(XmlCharacters.sanitize("a\u0000b\u0001c\u001Fd￾e￿f")).isEqualTo("abcdef");
    }

    @Test
    void removesLoneSurrogates() {
        assertThat(XmlCharacters.sanitize("x\uD800y\uDC00z")).isEqualTo("xyz");
    }
}

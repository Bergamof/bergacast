package fr.bergadev.bergacast.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiTokensTest {

    @Test
    void generatesPrefixedBase64UrlTokensOf32RandomBytes() {
        String token = ApiTokens.generate();

        assertThat(token).startsWith("bgc_").matches("bgc_[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(token.substring(4))).hasSize(32);
    }

    @Test
    void generatesDistinctTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(ApiTokens.generate());
        }

        assertThat(tokens).hasSize(100);
    }

    @Test
    void hashesWithSha256AsLowercaseHex() {
        assertThat(ApiTokens.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashIsDeterministicAndDiffersBetweenTokens() {
        String token = ApiTokens.generate();

        assertThat(ApiTokens.hash(token)).isEqualTo(ApiTokens.hash(token)).hasSize(64);
        assertThat(ApiTokens.hash(token)).isNotEqualTo(ApiTokens.hash(token + "x"));
    }

    @Test
    void displaysTheFirstEightCharacters() {
        assertThat(ApiTokens.displayedPrefix("bgc_abcdefgh")).isEqualTo("bgc_abcd");
    }

    @Test
    void recognisesApiTokensByTheirPrefix() {
        assertThat(ApiTokens.looksLikeApiToken("bgc_xyz")).isTrue();
        assertThat(ApiTokens.looksLikeApiToken("eyJhbGciOiJIUzI1NiJ9.e30.x")).isFalse();
    }
}

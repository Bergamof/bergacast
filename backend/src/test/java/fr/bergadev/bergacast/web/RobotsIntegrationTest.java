package fr.bergadev.bergacast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.support.IntegrationTest;
import fr.bergadev.bergacast.support.StoredObjects;
import fr.bergadev.bergacast.support.StoredObjects.StoredObject;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RobotsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoredObjects storedObjects;

    @Test
    void disallowsTheCrawlingOfTheWholeServer() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("User-agent: *\nDisallow: /\n"))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));
    }

    @Test
    void asksNotToIndexAnyResponse() throws Exception {
        mockMvc.perform(get("/robots.txt")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mockMvc.perform(get("/api/v1")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mockMvc.perform(get("/api/v1/podcasts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mockMvc.perform(get("/unknown-podcast.xml"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mockMvc.perform(get("/no/such/page"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    }

    /** The bucket serves the feeds and audio files: it needs the same rules, but cannot send X-Robots-Tag. */
    @Test
    void writesTheSameRulesAtTheRootOfTheBucketOnStartup() {
        StoredObject robotsTxt = storedObjects.get(MediaKeys.ROBOTS_TXT);

        assertThat(new String(robotsTxt.content(), StandardCharsets.UTF_8)).isEqualTo("User-agent: *\nDisallow: /\n");
        assertThat(robotsTxt.contentType()).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
        assertThat(robotsTxt.cacheControl()).isEqualTo("max-age=86400, public");
    }
}

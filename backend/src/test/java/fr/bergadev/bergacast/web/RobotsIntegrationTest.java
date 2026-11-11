package fr.bergadev.bergacast.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RobotsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disallowsTheCrawlingOfTheServerExceptTheApiDocumentation() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content()
                        .string(
                                """
                                User-agent: *
                                Allow: /swagger-ui.html
                                Allow: /swagger-ui/
                                Allow: /v3/api-docs
                                Disallow: /
                                """))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));
    }

    @Test
    void asksNotToIndexAnyResponse() throws Exception {
        mockMvc.perform(get("/robots.txt")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mockMvc.perform(get("/v3/api-docs")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
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

    /** Static resources are limited to robots.txt: nothing else of the classpath is served. */
    @Test
    void servesNoOtherStaticResource() throws Exception {
        mockMvc.perform(get("/application.yaml")).andExpect(status().isNotFound());
    }
}

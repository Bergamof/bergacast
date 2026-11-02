package fr.bergadev.bergacast.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class JsonRequestSizeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Test
    void refusesAJsonBodyAboveTheLimitWith413() throws Exception {
        mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, api.adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(podcastWithDescriptionOf(JsonRequestSizeFilter.MAX_JSON_BODY.toBytes())))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.detail").value("The JSON request content exceeds the maximum size of 256 KiB"));
    }

    @Test
    void acceptsAJsonBodyWithinTheLimit() throws Exception {
        String slug = ApiTestClient.uniqueSlug("json-size");
        mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, api.adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(podcast(slug, "d".repeat(100_000))))
                .andExpect(status().isCreated());
    }

    private static String podcastWithDescriptionOf(long length) {
        return podcast(ApiTestClient.uniqueSlug("json-size"), "d".repeat(Math.toIntExact(length)));
    }

    private static String podcast(String slug, String description) {
        return "{\"slug\":\"" + slug + "\",\"title\":\"Large\",\"description\":\"" + description
                + "\",\"language\":\"fr\"}";
    }
}

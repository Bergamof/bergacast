package io.github.bergamof.bergacast.token;

import static io.github.bergamof.bergacast.support.ApiTestClient.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.bergamof.bergacast.support.ApiTestClient;
import io.github.bergamof.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class ApiTokenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    private String slug;
    private String admin;

    @BeforeEach
    void createPodcast() throws Exception {
        slug = ApiTestClient.uniqueSlug("tokens");
        api.createPodcast(slug);
        admin = api.adminAuthorization();
    }

    @Test
    void returnsAnEmptyEmbeddedCollection() throws Exception {
        mockMvc.perform(get("/api/v1/podcasts/{slug}/api-tokens", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.apiTokens.length()").value(0))
                .andExpect(jsonPath("$._links.self.href").isString());
    }

    @Test
    void createsListsUsesAndRevokesATokenShownOnlyOnce() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/podcasts/{slug}/api-tokens", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Uploader\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                HttpHeaders.LOCATION, containsString("/api/v1/podcasts/" + slug + "/api-tokens/")))
                .andExpect(jsonPath("$.name").value("Uploader"))
                .andExpect(jsonPath("$.token").value(startsWith("bgc_")))
                .andExpect(jsonPath("$.lastUsedAt").isEmpty())
                .andReturn();
        JsonNode body = api.json(created);
        String token = body.get("token").asString();
        String id = body.get("id").asString();

        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/podcasts/{slug}/api-tokens", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.apiTokens[0].id").value(id))
                .andExpect(jsonPath("$._embedded.apiTokens[0].tokenPrefix").value(token.substring(0, 8)))
                .andExpect(jsonPath("$._embedded.apiTokens[0].lastUsedAt").isString())
                .andExpect(jsonPath("$._embedded.apiTokens[0].token").doesNotExist())
                .andExpect(jsonPath("$._embedded.apiTokens[0]._links.self.href").value(endsWith("/api-tokens/" + id)));

        mockMvc.perform(delete("/api/v1/podcasts/{slug}/api-tokens/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/podcasts/{slug}/api-tokens/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesTheName() throws Exception {
        mockMvc.perform(post("/api/v1/podcasts/{slug}/api-tokens", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
}

package fr.bergadev.bergacast.security;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** CORS is enabled on the API only, for the admin SPA origin configured in the test profile. */
@IntegrationTest
class CorsIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://admin.example.test";
    private static final String FOREIGN_ORIGIN = "https://evil.example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Test
    void answersAPreflightFromTheAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/podcasts")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,if-match"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")))
                .andExpect(header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                                allOf(
                                        containsString("authorization"),
                                        containsString("content-type"),
                                        containsString("if-match"))))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void allowsTheIdempotencyKeyOfEpisodeUploads() throws Exception {
        mockMvc.perform(options("/api/v1/{slug}/episodes", "any-podcast")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(
                        header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("idempotency-key")));
    }

    @Test
    void rejectsAPreflightFromAnotherOriginWithProblemDetails() throws Exception {
        mockMvc.perform(options("/api/v1/podcasts")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/api/v1/podcasts"));
    }

    @Test
    void rejectsAnActualRequestFromAnotherOriginWithProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1").header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void letsTheAllowedOriginReadSecurityErrors() throws Exception {
        mockMvc.perform(get("/api/v1/podcasts").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void exposesEntityTagAndLocationToTheAllowedOrigin() throws Exception {
        String slug = ApiTestClient.uniqueSlug("cors");
        api.createPodcast(slug);

        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, api.adminAuthorization()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(
                                HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                                allOf(containsString(HttpHeaders.ETAG), containsString(HttpHeaders.LOCATION))))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void addsNoCorsHeadersOutsideOfTheApi() throws Exception {
        String slug = ApiTestClient.uniqueSlug("cors-feed");
        api.createPodcast(slug);

        mockMvc.perform(get("/{slug}.xml", slug).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}

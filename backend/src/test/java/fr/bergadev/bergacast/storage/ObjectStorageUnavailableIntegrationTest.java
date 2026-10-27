package fr.bergadev.bergacast.storage;

import static fr.bergadev.bergacast.support.ApiTestClient.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.image.TestImages;
import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every write to the object storage fails (the configured bucket does not exist): changes already committed are kept
 * and reported as {@code 503}, the others are refused before anything is saved.
 */
@IntegrationTest
@TestPropertySource(properties = "bergacast.storage.bucket=missing-bucket")
class ObjectStorageUnavailableIntegrationTest {

    private static final String MERGE_PATCH = "application/merge-patch+json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    private String admin;
    private String slug;

    @BeforeEach
    void logIn() throws Exception {
        admin = api.adminAuthorization();
        slug = ApiTestClient.uniqueSlug("unavailable");
    }

    @Test
    void savesAPodcastWhoseFeedCannotBeWrittenAndTellsHowToWriteItLater() throws Exception {
        createPodcast()
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail")
                        .value(containsString("POST /api/v1/podcasts/" + slug + "/feed-publications")));

        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    @Test
    void savesAPodcastChangeWhoseFeedCannotBeWritten() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());

        mockMvc.perform(patch("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.title").value("Renamed"));
    }

    @Test
    void answers503WhenTheFeedCannotBeWrittenOnDemand() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/v1/podcasts/{slug}/feed-publications", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void publishesNoEpisodeWhoseAudioCannotBeWritten() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());
        String token = bearer(api.createApiToken(slug));

        mockMvc.perform(ApiTestClient.uploadRequest(slug, "{\"title\":\"Lost\"}", new byte[] {1}, "audio/mpeg")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("nothing was changed")));

        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void setsNoImageThatCannotBeWritten() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());
        String etag = podcastEntityTag();

        mockMvc.perform(put("/api/v1/podcasts/{slug}/image", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(TestImages.png(10, 10)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("nothing was changed")));

        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    /** Removing the objects of a deleted podcast is best effort: orphans are logged, the deletion stands. */
    @Test
    void deletesAPodcastWhoseObjectsCannotBeRemoved() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());

        mockMvc.perform(delete("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    private ResultActions createPodcast() throws Exception {
        return mockMvc.perform(post("/api/v1/podcasts")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + slug + "\",\"title\":\"t\",\"description\":\"d\",\"language\":\"fr\"}"));
    }

    private String podcastEntityTag() throws Exception {
        return mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
    }
}

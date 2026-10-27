package fr.bergadev.bergacast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The former media URLs of the backend, known by subscribed podcast apps, are permanently redirected to the same path
 * on the object storage, whether the podcast exists or not (the database is not queried).
 */
@IntegrationTest
class MediaRedirectIntegrationTest {

    private static final String EPISODE_ID = "3f2c8a1e-5b7d-4c9e-8f10-0123456789ab";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "/my-podcast.xml",
                "/my-podcast/image.jpg",
                "/my-podcast/image.png",
                "/my-podcast/episodes/" + EPISODE_ID + "/audio.mp3",
                "/my-podcast/episodes/" + EPISODE_ID + "/audio.m4a"
            })
    void redirectsPermanentlyToTheSamePathOnTheObjectStorage(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://media.example.test" + path))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, public"));
    }

    @Test
    void answersHeadRequestsWithoutBody() throws Exception {
        MvcResult result = mockMvc.perform(head("/my-podcast/episodes/{id}/audio.mp3", EPISODE_ID))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(
                                HttpHeaders.LOCATION,
                                "https://media.example.test/my-podcast/episodes/" + EPISODE_ID + "/audio.mp3"))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void ignoresCredentialsOnPublicResources() throws Exception {
        mockMvc.perform(get("/my-podcast.xml").header(HttpHeaders.AUTHORIZATION, "Bearer junk"))
                .andExpect(status().isMovedPermanently());
        mockMvc.perform(get("/my-podcast/image.png").header(HttpHeaders.AUTHORIZATION, "Bearer junk"))
                .andExpect(status().isMovedPermanently());
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, "Bearer junk"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/podcasts").header(HttpHeaders.AUTHORIZATION, "Bearer junk"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "/my-podcast/image.jpeg",
                "/My-Podcast.xml",
                "/my-podcast/episodes/not-a-uuid/audio.mp3",
                "/my-podcast/episodes/" + EPISODE_ID + "/audio.MP3"
            })
    void redirectsNothingButTheFormerMediaUrls(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void keepsTheEpisodeIdOfTheRequestInTheTarget() throws Exception {
        String episodeId = UUID.randomUUID().toString();

        mockMvc.perform(get("/other/episodes/{id}/audio.ogg", episodeId))
                .andExpect(header().string(
                                HttpHeaders.LOCATION,
                                "https://media.example.test/other/episodes/" + episodeId + "/audio.ogg"));
    }
}

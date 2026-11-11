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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
    private static final int SHA256_LENGTH = 64;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void setsNoEpisodeImageThatCannotBeWritten() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());
        UUID episodeId = insertEpisode(false);
        String etag = episodeEntityTag(episodeId);

        mockMvc.perform(put(episodeImagePath(episodeId))
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(TestImages.png(10, 10)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("nothing was changed")));

        mockMvc.perform(get(episodeImagePath(episodeId)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(episodePath(episodeId)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void publishesNoEpisodeWhoseImageIsSentWithAnAudioThatCannotBeWritten() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());

        mockMvc.perform(ApiTestClient.uploadRequest(slug, "{\"title\":\"Lost\"}", new byte[] {1}, "audio/mpeg")
                        .file(new MockMultipartFile(
                                "image", "cover.png", MediaType.IMAGE_PNG_VALUE, TestImages.png(10, 10)))
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("nothing was changed")));

        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    /** The image is removed from the episode; only the feed, written after the commit, cannot be updated. */
    @Test
    void deletesAnEpisodeImageWhoseFeedCannotBeWritten() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());
        UUID episodeId = insertEpisode(true);

        mockMvc.perform(delete(episodeImagePath(episodeId)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("feed-publications")));

        mockMvc.perform(get(episodePath(episodeId)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").isEmpty());
    }

    /** Removing the objects of a deleted episode is best effort: orphans are logged, the deletion stands. */
    @Test
    void deletesAnEpisodeWithAnImageWhoseObjectsCannotBeRemoved() throws Exception {
        createPodcast().andExpect(status().isServiceUnavailable());
        UUID episodeId = insertEpisode(true);

        mockMvc.perform(delete(episodePath(episodeId)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("feed-publications")));

        mockMvc.perform(get(episodePath(episodeId)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
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

    /** An episode cannot be published while the storage is down: it is inserted as if published before. */
    private UUID insertEpisode(boolean withImage) {
        UUID audioFileId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        UUID podcastId = jdbcTemplate.queryForObject("select id from podcast where slug = ?", UUID.class, slug);
        jdbcTemplate.update(
                "insert into audio_file (id, mime_type, size_bytes, sha256, file_extension) values (?, ?, ?, ?, ?)",
                audioFileId,
                "audio/mpeg",
                1L,
                "a".repeat(SHA256_LENGTH),
                "mp3");
        jdbcTemplate.update(
                "insert into episode (id, podcast_id, audio_file_id, guid, title, publication_date, added_at,"
                        + " episode_type, version, image_mime_type, image_width, image_height, image_size_bytes,"
                        + " image_sha256) values (?, ?, ?, ?, ?, now(), now(), 'FULL', 0, ?, ?, ?, ?, ?)",
                episodeId,
                podcastId,
                audioFileId,
                "guid-" + episodeId,
                "Inserted",
                withImage ? "image/png" : null,
                withImage ? 10 : null,
                withImage ? 10 : null,
                withImage ? 1L : null,
                withImage ? "b".repeat(SHA256_LENGTH) : null);
        return episodeId;
    }

    private String episodePath(UUID episodeId) {
        return "/api/v1/" + slug + "/episodes/" + episodeId;
    }

    private String episodeImagePath(UUID episodeId) {
        return episodePath(episodeId) + "/image";
    }

    private String episodeEntityTag(UUID episodeId) throws Exception {
        return mockMvc.perform(get(episodePath(episodeId)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
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

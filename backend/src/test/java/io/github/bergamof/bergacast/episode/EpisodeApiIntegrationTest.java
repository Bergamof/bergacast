package io.github.bergamof.bergacast.episode;

import static io.github.bergamof.bergacast.support.ApiTestClient.bearer;
import static io.github.bergamof.bergacast.support.ApiTestClient.uploadRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.bergamof.bergacast.support.ApiTestClient;
import io.github.bergamof.bergacast.support.IntegrationTest;
import io.github.bergamof.bergacast.web.Sha256;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class EpisodeApiIntegrationTest {

    private static final byte[] AUDIO = "ID3 fake mp3 content".getBytes(StandardCharsets.UTF_8);
    private static final String MERGE_PATCH = "application/merge-patch+json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    private String slug;
    private String token;
    private String admin;

    @BeforeEach
    void createPodcast() throws Exception {
        slug = ApiTestClient.uniqueSlug("episodes");
        api.createPodcast(slug);
        token = bearer(api.createApiToken(slug));
        admin = api.adminAuthorization();
    }

    @Test
    void publishesAnEpisodeWithAnApiToken() throws Exception {
        String metadata = """
                {"title":"First","description":"Notes","publicationDate":"2026-01-02T10:00:00+02:00",
                 "guid":"my-guid","durationSeconds":61,"season":1,"episodeNumber":3,
                 "episodeType":"TRAILER","explicit":true}""";
        MvcResult result = mockMvc.perform(
                        uploadRequest(slug, metadata, AUDIO, "audio/mpeg").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/" + slug + "/episodes/")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.title").value("First"))
                .andExpect(jsonPath("$.guid").value("my-guid"))
                .andExpect(jsonPath("$.publicationDate").value("2026-01-02T08:00:00Z"))
                .andExpect(jsonPath("$.addedAt").isString())
                .andExpect(jsonPath("$.durationSeconds").value(61))
                .andExpect(jsonPath("$.season").value(1))
                .andExpect(jsonPath("$.episodeNumber").value(3))
                .andExpect(jsonPath("$.episodeType").value("TRAILER"))
                .andExpect(jsonPath("$.explicit").value(true))
                .andExpect(jsonPath("$.audio.mimeType").value("audio/mpeg"))
                .andExpect(jsonPath("$.audio.sizeBytes").value(AUDIO.length))
                .andExpect(jsonPath("$.audio.sha256").value(Sha256.hex(AUDIO)))
                .andExpect(jsonPath("$.audio.url").value(endsWith("/audio.mp3")))
                .andExpect(jsonPath("$._links.self.href").isString())
                .andExpect(jsonPath("$._links.podcast.href").value(endsWith("/api/v1/podcasts/" + slug)))
                .andExpect(jsonPath("$._links.audio.href")
                        .value(startsWith("https://podcasts.example.test/" + slug + "/episodes/")))
                .andReturn();
        JsonNode episode = api.json(result);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .endsWith("/episodes/" + episode.get("id").asString());
        assertThat(api.largeObjectExists(api.audioOidOfEpisode(episode.get("id").asString())))
                .isTrue();
    }

    @Test
    void appliesDefaultsToOptionalMetadata() throws Exception {
        JsonNode episode = api.publishEpisode(slug, admin, "{\"title\":\"Minimal\"}", AUDIO);

        assertThat(episode.get("guid").asString()).matches("[0-9a-f-]{36}");
        assertThat(episode.get("episodeType").asString()).isEqualTo("FULL");
        assertThat(episode.get("publicationDate").asString()).isNotBlank();
        assertThat(episode.get("explicit").isNull()).isTrue();
    }

    @Test
    void replaysAnUploadWithTheSameIdempotencyKey() throws Exception {
        MvcResult first = mockMvc.perform(uploadRequest(slug, "{\"title\":\"Once\"}", AUDIO, "audio/mpeg")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", "upload-42"))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult retry = mockMvc.perform(uploadRequest(slug, "{\"title\":\"Once\"}", AUDIO, "audio/mpeg")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", "upload-42"))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(retry.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(first.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(api.json(retry).get("id")).isEqualTo(api.json(first).get("id"));
        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void rejectsADuplicateGuid() throws Exception {
        api.publishEpisode(slug, token, "{\"title\":\"A\",\"guid\":\"same\"}", AUDIO);

        mockMvc.perform(uploadRequest(slug, "{\"title\":\"B\",\"guid\":\"same\"}", AUDIO, "audio/mpeg")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsNonAudioFiles() throws Exception {
        mockMvc.perform(uploadRequest(slug, "{\"title\":\"Image\"}", AUDIO, "image/png")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void validatesTheMetadata() throws Exception {
        mockMvc.perform(uploadRequest(slug, "{\"title\":\"\",\"season\":0}", AUDIO, "audio/mpeg")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("season"))
                .andExpect(jsonPath("$.errors[1].field").value("title"));
    }

    @Test
    void requiresTheAudioPart() throws Exception {
        mockMvc.perform(multipart("/api/v1/{slug}/episodes", slug)
                        .file(new MockMultipartFile(
                                "metadata",
                                "",
                                "application/json",
                                "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returns404ForAnUnknownPodcastOrEpisode() throws Exception {
        mockMvc.perform(get("/api/v1/{slug}/episodes", "no-such-podcast").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/{slug}/episodes/{id}", slug, "00000000-0000-0000-0000-000000000000")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void listsEpisodesByPublicationDateDescending() throws Exception {
        api.publishEpisode(slug, token, "{\"title\":\"Old\",\"publicationDate\":\"2025-01-01T00:00:00Z\"}", AUDIO);
        api.publishEpisode(slug, token, "{\"title\":\"New\",\"publicationDate\":\"2026-01-01T00:00:00Z\"}", AUDIO);
        api.publishEpisode(slug, token, "{\"title\":\"Mid\",\"publicationDate\":\"2025-06-01T00:00:00Z\"}", AUDIO);

        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.episodes[0].title").value("New"))
                .andExpect(jsonPath("$._embedded.episodes[1].title").value("Mid"))
                .andExpect(jsonPath("$._embedded.episodes[2].title").value("Old"))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        mockMvc.perform(get("/api/v1/{slug}/episodes?sort=publicationDate,asc", slug)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$._embedded.episodes[0].title").value("Old"));
    }

    @Test
    void returnsAnEmptyEmbeddedCollection() throws Exception {
        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.episodes.length()").value(0));
    }

    @Test
    void getsPatchesAndDeletesAnEpisode() throws Exception {
        JsonNode episode = api.publishEpisode(slug, token, "{\"title\":\"Draft\",\"guid\":\"g1\"}", AUDIO);
        String id = episode.get("id").asString();
        long oid = api.audioOidOfEpisode(id);

        mockMvc.perform(get("/api/v1/{slug}/episodes/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_NONE_MATCH, "\"0\""))
                .andExpect(status().isNotModified());
        mockMvc.perform(patch("/api/v1/{slug}/episodes/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Final\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/{slug}/episodes/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Final\"}"))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(patch("/api/v1/{slug}/episodes/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MERGE_PATCH)
                        .content("{\"publicationDate\":null}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("publicationDate"));
        mockMvc.perform(patch("/api/v1/{slug}/episodes/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Final\",\"season\":2,\"explicit\":false}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.title").value("Final"))
                .andExpect(jsonPath("$.guid").value("g1"))
                .andExpect(jsonPath("$.season").value(2))
                .andExpect(jsonPath("$.explicit").value(false));

        mockMvc.perform(delete("/api/v1/{slug}/episodes/{id}", slug, id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/{slug}/episodes/{id}", slug, id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        assertThat(api.largeObjectExists(oid)).isFalse();
        mockMvc.perform(get("/api/v1/{slug}/episodes/{id}", slug, id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAGuidChangeToAnExistingGuid() throws Exception {
        api.publishEpisode(slug, token, "{\"title\":\"A\",\"guid\":\"taken\"}", AUDIO);
        String id = api.publishEpisode(slug, token, "{\"title\":\"B\",\"guid\":\"free\"}", AUDIO)
                .get("id")
                .asString();

        mockMvc.perform(patch("/api/v1/{slug}/episodes/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MERGE_PATCH)
                        .content("{\"guid\":\"taken\"}"))
                .andExpect(status().isConflict());
    }
}

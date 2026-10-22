package io.github.bergamof.bergacast.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shortcuts to set up data through the HTTP API in integration tests. */
public class ApiTestClient {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin-password-123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private final JdbcTemplate jdbcTemplate;

    public ApiTestClient(MockMvc mockMvc, JsonMapper jsonMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static String uniqueSlug(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/access-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asString();
    }

    public String adminAuthorization() throws Exception {
        return bearer(adminToken());
    }

    public JsonNode createPodcast(String slug) throws Exception {
        return createPodcast(slug, "");
    }

    /** Creates a podcast; {@code extraJsonMembers} is inserted in the JSON body, e.g. {@code ,"feed":{...}}. */
    public JsonNode createPodcast(String slug, String extraJsonMembers) throws Exception {
        String body = "{\"slug\":\"" + slug + "\",\"title\":\"Podcast " + slug + "\",\"description\":\"About " + slug
                + "\",\"language\":\"fr\"" + extraJsonMembers + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result);
    }

    public String createApiToken(String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/podcasts/{slug}/api-tokens", slug)
                        .header(HttpHeaders.AUTHORIZATION, adminAuthorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CI\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("token").asString();
    }

    public static MockMultipartHttpServletRequestBuilder uploadRequest(
            String slug, String metadataJson, byte[] audio, String audioContentType) {
        return multipart("/api/v1/{slug}/episodes", slug)
                .part(metadataPart(MediaType.APPLICATION_JSON_VALUE, metadataJson))
                .file(new MockMultipartFile("audio", "episode.mp3", audioContentType, audio));
    }

    /** A form field (no file name) named {@code metadata}, as sent by HTTP clients. */
    public static MockPart metadataPart(@Nullable String contentType, String metadataJson) {
        MockPart part = new MockPart("metadata", metadataJson.getBytes(StandardCharsets.UTF_8));
        if (contentType != null) {
            part.getHeaders().setContentType(MediaType.parseMediaType(contentType));
        }
        return part;
    }

    public JsonNode publishEpisode(String slug, String authorization, String metadataJson, byte[] audio)
            throws Exception {
        MvcResult result = mockMvc.perform(uploadRequest(slug, metadataJson, audio, "audio/mpeg")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result);
    }

    public JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Whether the large object holding the audio of an episode still exists. */
    public boolean largeObjectExists(long oid) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from pg_largeobject_metadata where oid = ?", Integer.class, oid);
        return count != null && count > 0;
    }

    public long audioOidOfEpisode(String episodeId) {
        Long oid = jdbcTemplate.queryForObject(
                "select a.content_oid from audio_file a join episode e on e.audio_file_id = a.id where e.id = ?",
                Long.class,
                UUID.fromString(episodeId));
        if (oid == null) {
            throw new IllegalStateException("No audio for episode " + episodeId);
        }
        return oid;
    }
}

package io.github.bergamof.bergacast.feed;

import static io.github.bergamof.bergacast.support.ApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.bergamof.bergacast.support.ApiTestClient;
import io.github.bergamof.bergacast.support.IntegrationTest;
import io.github.bergamof.bergacast.web.Sha256;
import java.net.URI;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class PublicFeedIntegrationTest {

    private static final byte[] AUDIO = sequentialBytes(1000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    private String slug;
    private String token;

    @BeforeEach
    void createPodcast() throws Exception {
        slug = ApiTestClient.uniqueSlug("feed");
        api.createPodcast(slug, ",\"feed\":{\"maxEpisodes\":2},\"category\":\"Technology\"");
        token = bearer(api.createApiToken(slug));
    }

    @Test
    void servesTheFeedPublicly() throws Exception {
        api.publishEpisode(slug, token, "{\"title\":\"Oldest\",\"publicationDate\":\"2024-01-01T00:00:00Z\"}", AUDIO);
        api.publishEpisode(slug, token, "{\"title\":\"Older\",\"publicationDate\":\"2025-01-01T00:00:00Z\"}", AUDIO);
        api.publishEpisode(slug, token, "{\"title\":\"Recent\",\"publicationDate\":\"2026-01-01T00:00:00Z\"}", AUDIO);
        api.publishEpisode(slug, token, "{\"title\":\"Future\",\"publicationDate\":\"2099-01-01T00:00:00Z\"}", AUDIO);

        mockMvc.perform(get("/{slug}.xml", slug))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/rss+xml;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(content().string(containsString("<title>Podcast " + slug + "</title>")))
                .andExpect(content().string(containsString("<itunes:category text=\"Technology\"")))
                .andExpect(content()
                        .string(containsString("<atom:link href=\"https://podcasts.example.test/" + slug + ".xml\"")))
                .andExpect(content().string(containsString("<title>Recent</title>")))
                .andExpect(content().string(containsString("<title>Older</title>")))
                .andExpect(content().string(not(containsString("<title>Oldest</title>"))))
                .andExpect(content().string(not(containsString("<title>Future</title>"))))
                .andExpect(content().string(containsString("length=\"1000\" type=\"audio/mpeg\"")));
    }

    @Test
    void supportsConditionalRequestsOnTheFeed() throws Exception {
        String etag = mockMvc.perform(get("/{slug}.xml", slug))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/{slug}.xml", slug).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());

        api.publishEpisode(slug, token, "{\"title\":\"News\",\"publicationDate\":\"2026-01-01T00:00:00Z\"}", AUDIO);
        mockMvc.perform(get("/{slug}.xml", slug).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk());
    }

    @Test
    void answersAProblemForAnUnknownFeed() throws Exception {
        mockMvc.perform(get("/no-such-podcast.xml"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void servesTheWholeAudioFile() throws Exception {
        String audioPath = audioPathOf(publish());

        MvcResult result = mockMvc.perform(get(audioPath))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "1000"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + Sha256.hex(AUDIO) + "\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(AUDIO);
    }

    @Test
    void servesAByteRange() throws Exception {
        String audioPath = audioPathOf(publish());

        MvcResult result = mockMvc.perform(get(audioPath).header(HttpHeaders.RANGE, "bytes=100-199"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 100-199/1000"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "100"))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(Arrays.copyOfRange(AUDIO, 100, 200));

        MvcResult suffix = mockMvc.perform(get(audioPath).header(HttpHeaders.RANGE, "bytes=-10"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 990-999/1000"))
                .andReturn();
        assertThat(suffix.getResponse().getContentAsByteArray()).isEqualTo(Arrays.copyOfRange(AUDIO, 990, 1000));
    }

    @Test
    void rejectsAnUnsatisfiableRange() throws Exception {
        String audioPath = audioPathOf(publish());

        mockMvc.perform(get(audioPath).header(HttpHeaders.RANGE, "bytes=5000-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */1000"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void answersHeadAndConditionalAudioRequests() throws Exception {
        String audioPath = audioPathOf(publish());

        MvcResult headResult = mockMvc.perform(head(audioPath))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "1000"))
                .andReturn();
        assertThat(headResult.getResponse().getContentAsByteArray()).isEmpty();
        mockMvc.perform(get(audioPath).header(HttpHeaders.IF_NONE_MATCH, "\"" + Sha256.hex(AUDIO) + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void doesNotServeAudioThroughAnotherPodcast() throws Exception {
        JsonNode episode = publish();
        String otherSlug = ApiTestClient.uniqueSlug("other");
        api.createPodcast(otherSlug);

        mockMvc.perform(get(
                        "/{slug}/episodes/{id}/audio.mp3",
                        otherSlug,
                        episode.get("id").asString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private JsonNode publish() throws Exception {
        return api.publishEpisode(slug, token, "{\"title\":\"Audio\"}", AUDIO);
    }

    private static byte[] sequentialBytes(int length) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) {
            bytes[index] = (byte) index;
        }
        return bytes;
    }

    private static String audioPathOf(JsonNode episode) {
        return URI.create(episode.get("audio").get("url").asString()).getPath();
    }
}

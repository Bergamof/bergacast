package fr.bergadev.bergacast.feed;

import static fr.bergadev.bergacast.support.ApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import fr.bergadev.bergacast.support.StoredObjects;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class FeedPublicationApiIntegrationTest {

    private static final byte[] AUDIO = {1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private StoredObjects storedObjects;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String slug;
    private String admin;

    @BeforeEach
    void createPodcast() throws Exception {
        slug = ApiTestClient.uniqueSlug("feed-publication");
        api.createPodcast(slug);
        admin = api.adminAuthorization();
    }

    @Test
    void writesTheFeedAndDescribesThePublication() throws Exception {
        api.publishEpisode(slug, admin, "{\"title\":\"Listed\"}", AUDIO);
        storedObjects.put(MediaKeys.feed(slug), "stale".getBytes(StandardCharsets.UTF_8));
        String feedUrl = "https://media.example.test/" + slug + ".xml";
        Instant before = Instant.now();

        MvcResult result = mockMvc.perform(publishFeed(slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$.feedUrl").value(feedUrl))
                .andExpect(jsonPath("$.episodeCount").value(1))
                .andExpect(jsonPath("$._links.podcast.href").value(endsWith("/api/v1/podcasts/" + slug)))
                .andExpect(jsonPath("$._links.feed.href").value(feedUrl))
                .andReturn();

        assertThat(Instant.parse(api.json(result).get("publishedAt").asString()))
                .isBetween(before, Instant.now());
        assertThat(new String(storedObjects.get(MediaKeys.feed(slug)).content(), StandardCharsets.UTF_8))
                .contains("<title>Listed</title>");
    }

    /** The feed is a snapshot: a scheduled episode appears at the first write after its publication date. */
    @Test
    void listsAScheduledEpisodeOnceItsPublicationDateHasPassed() throws Exception {
        JsonNode scheduled = api.publishEpisode(
                slug, admin, "{\"title\":\"Scheduled\",\"publicationDate\":\"2099-01-01T00:00:00Z\"}", AUDIO);
        assertThat(feedText()).doesNotContain("<title>Scheduled</title>");
        // Time passes: the publication date is now behind, without any change that would write the feed.
        jdbcTemplate.update(
                "update episode set publication_date = ? where id = ?",
                Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")),
                UUID.fromString(scheduled.get("id").asString()));
        assertThat(feedText()).doesNotContain("<title>Scheduled</title>");

        mockMvc.perform(publishFeed(slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodeCount").value(1));

        assertThat(feedText()).contains("<title>Scheduled</title>");
    }

    @Test
    void answers404ForAnUnknownPodcast() throws Exception {
        mockMvc.perform(publishFeed("no-such-podcast").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
        assertThat(storedObjects.find(MediaKeys.feed("no-such-podcast"))).isEmpty();
    }

    @Test
    void isReservedToAdministrators() throws Exception {
        String podcastToken = bearer(api.createApiToken(slug));

        mockMvc.perform(publishFeed(slug))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(publishFeed(slug).header(HttpHeaders.AUTHORIZATION, podcastToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static MockHttpServletRequestBuilder publishFeed(String podcastSlug) {
        return post("/api/v1/podcasts/{slug}/feed-publications", podcastSlug);
    }

    private String feedText() {
        return new String(storedObjects.get(MediaKeys.feed(slug)).content(), StandardCharsets.UTF_8);
    }
}

package fr.bergadev.bergacast.feed;

import static fr.bergadev.bergacast.support.ApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import fr.bergadev.bergacast.support.StoredObjects;
import fr.bergadev.bergacast.support.StoredObjects.StoredObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.w3c.dom.Document;
import tools.jackson.databind.JsonNode;

/** The feed of a podcast is written to the object storage after every change of what it shows. */
@IntegrationTest
class PublishedFeedIntegrationTest {

    private static final byte[] AUDIO = new byte[1000];
    private static final String MERGE_PATCH = "application/merge-patch+json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private StoredObjects storedObjects;

    private String slug;
    private String token;
    private String admin;

    @BeforeEach
    void createPodcast() throws Exception {
        slug = ApiTestClient.uniqueSlug("feed");
        api.createPodcast(slug, ",\"feed\":{\"maxEpisodes\":2},\"category\":\"Technology\"");
        token = bearer(api.createApiToken(slug));
        admin = api.adminAuthorization();
    }

    @Test
    void writesTheFeedWhenThePodcastIsCreated() {
        StoredObject feed = storedObjects.get(MediaKeys.feed(slug));

        assertThat(feed.contentType()).isEqualTo("application/rss+xml;charset=UTF-8");
        assertThat(feed.cacheControl()).isEqualTo("max-age=300, public");
        assertThat(text(feed))
                .contains("<title>Podcast " + slug + "</title>")
                .contains("<itunes:category text=\"Technology\"")
                .contains("<atom:link href=\"https://media.example.test/" + slug + ".xml\"")
                .contains("<itunes:block>yes</itunes:block>")
                .doesNotContain("<item>");
    }

    @Test
    void listsThePublishedEpisodesAccordingToTheFeedSettings() throws Exception {
        api.publishEpisode(slug, token, "{\"title\":\"Oldest\",\"publicationDate\":\"2024-01-01T00:00:00Z\"}", AUDIO);
        api.publishEpisode(slug, token, "{\"title\":\"Older\",\"publicationDate\":\"2025-01-01T00:00:00Z\"}", AUDIO);
        JsonNode recent = api.publishEpisode(
                slug, token, "{\"title\":\"Recent\",\"publicationDate\":\"2026-01-01T00:00:00Z\"}", AUDIO);
        api.publishEpisode(slug, token, "{\"title\":\"Future\",\"publicationDate\":\"2099-01-01T00:00:00Z\"}", AUDIO);

        assertThat(feedText())
                .contains("<title>Recent</title>")
                .contains("<title>Older</title>")
                .doesNotContain("<title>Oldest</title>")
                .doesNotContain("<title>Future</title>")
                .contains("<enclosure url=\"" + recent.get("audio").get("url").asString()
                        + "\" length=\"1000\" type=\"audio/mpeg\"");
        assertThat(recent.get("audio").get("url").asString()).startsWith(ApiTestClient.MEDIA_BASE_URL + slug + "/");
    }

    @Test
    void producesWellFormedXmlFromControlCharacters() throws Exception {
        api.publishEpisode(slug, token, "{\"title\":\"Bell\\u0007 and \\u0001 start\"}", AUDIO);

        byte[] feed = storedObjects.get(MediaKeys.feed(slug)).content();

        Document document =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(feed));
        assertThat(document.getElementsByTagName("item").item(0).getFirstChild().getTextContent())
                .isEqualTo("Bell and  start");
    }

    @Test
    void writesTheFeedAgainWhenThePodcastIsPatched() throws Exception {
        api.publishEpisode(slug, token, "{\"title\":\"Future\",\"publicationDate\":\"2099-01-01T00:00:00Z\"}", AUDIO);

        mockMvc.perform(patch("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Renamed\",\"feed\":{\"includeFutureEpisodes\":true}}"))
                .andExpect(status().isOk());

        assertThat(feedText()).contains("<title>Renamed</title>").contains("<title>Future</title>");
    }

    @Test
    void writesTheFeedAgainWhenAnEpisodeIsPatchedThenDeleted() throws Exception {
        String id = api.publishEpisode(slug, token, "{\"title\":\"Draft\"}", AUDIO)
                .get("id")
                .asString();
        assertThat(feedText()).contains("<title>Draft</title>");

        mockMvc.perform(patch("/api/v1/{slug}/episodes/{id}", slug, id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Final\"}"))
                .andExpect(status().isOk());
        assertThat(feedText()).contains("<title>Final</title>").doesNotContain("<title>Draft</title>");

        mockMvc.perform(delete("/api/v1/{slug}/episodes/{id}", slug, id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        assertThat(feedText()).doesNotContain("<item>");
    }

    private String feedText() {
        return text(storedObjects.get(MediaKeys.feed(slug)));
    }

    private static String text(StoredObject object) {
        return new String(object.content(), StandardCharsets.UTF_8);
    }
}

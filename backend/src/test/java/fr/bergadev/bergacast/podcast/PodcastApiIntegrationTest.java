package fr.bergadev.bergacast.podcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class PodcastApiIntegrationTest {

    private static final String MERGE_PATCH = "application/merge-patch+json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    private String admin;

    @BeforeEach
    void logIn() throws Exception {
        admin = api.adminAuthorization();
    }

    @Test
    void createsAPodcastWithDefaultFeedSettings() throws Exception {
        String slug = ApiTestClient.uniqueSlug("create");
        mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","title":"My show","description":"Weekly","language":"fr-FR",
                                 "author":"Me","ownerEmail":"me@example.test","websiteUrl":"https://example.test"}""".formatted(slug)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/podcasts/" + slug)))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0.0\""))
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.explicit").value(false))
                .andExpect(jsonPath("$.feed.maxEpisodes").isEmpty())
                .andExpect(jsonPath("$.feed.evictOldestBy").value("PUBLICATION_DATE"))
                .andExpect(jsonPath("$.feed.sortBy").value("PUBLICATION_DATE"))
                .andExpect(jsonPath("$.feed.sortDirection").value("DESC"))
                .andExpect(jsonPath("$.feed.includeFutureEpisodes").value(false))
                .andExpect(jsonPath("$.feedUrl").value("https://podcasts.example.test/" + slug + ".xml"))
                .andExpect(jsonPath("$.publishUrl").value("https://podcasts.example.test/api/v1/" + slug + "/episodes"))
                .andExpect(jsonPath("$.episodeCount").value(0))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$._links.self.href").value(endsWith("/api/v1/podcasts/" + slug)))
                .andExpect(jsonPath("$._links.episodes.href").value(endsWith("/api/v1/" + slug + "/episodes")))
                .andExpect(
                        jsonPath("$._links.apiTokens.href").value(endsWith("/api/v1/podcasts/" + slug + "/api-tokens")))
                .andExpect(jsonPath("$._links.feed.href").value("https://podcasts.example.test/" + slug + ".xml"));
    }

    @Test
    void rejectsADuplicateSlug() throws Exception {
        String slug = ApiTestClient.uniqueSlug("duplicate");
        api.createPodcast(slug);

        mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"" + slug
                                + "\",\"title\":\"t\",\"description\":\"d\",\"language\":\"fr\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void reportsEveryValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"Not A Slug","title":"","description":"d","language":"french!",
                                 "ownerEmail":"nope","feed":{"maxEpisodes":0}}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("slug")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("language")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("ownerEmail")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("feed.maxEpisodes")));
    }

    @Test
    void rejectsReservedSlugs() throws Exception {
        mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"admin\",\"title\":\"t\",\"description\":\"d\",\"language\":\"fr\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("slug"))
                .andExpect(jsonPath("$.errors[0].message").value("is reserved by the application"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/podcasts")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void supportsConditionalGet() throws Exception {
        String slug = ApiTestClient.uniqueSlug("etag");
        api.createPodcast(slug);
        MvcResult result = mockMvc.perform(
                        get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn();
        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void patchesWithJsonMergePatchAndOptimisticLocking() throws Exception {
        String slug = ApiTestClient.uniqueSlug("patch");
        api.createPodcast(slug, ",\"author\":\"Someone\",\"feed\":{\"sortDirection\":\"ASC\"}");

        mockMvc.perform(patch("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_MATCH, "\"42.0\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"Too late\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(patch("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_MATCH, "\"0.0\"")
                        .contentType(MERGE_PATCH)
                        .content("{\"title\":\"New title\",\"author\":null,\"feed\":{\"maxEpisodes\":5}}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1.0\""))
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.author").isEmpty())
                .andExpect(jsonPath("$.description").value("About " + slug))
                .andExpect(jsonPath("$.feed.maxEpisodes").value(5))
                .andExpect(jsonPath("$.feed.sortDirection").value("ASC"));
    }

    @Test
    void refusesToChangeTheSlugOrToBreakValidation() throws Exception {
        String slug = ApiTestClient.uniqueSlug("immutable");
        api.createPodcast(slug);

        mockMvc.perform(patch("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MERGE_PATCH)
                        .content("{\"slug\":\"another-slug\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
        mockMvc.perform(patch("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MERGE_PATCH)
                        .content("{\"slug\":\"" + slug + "\",\"title\":null}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mockMvc.perform(patch("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void listsPodcastsAsAPagedHalCollection() throws Exception {
        api.createPodcast(ApiTestClient.uniqueSlug("list"));
        api.createPodcast(ApiTestClient.uniqueSlug("list"));

        mockMvc.perform(get("/api/v1/podcasts?size=1&page=0").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.podcasts.length()").value(1))
                .andExpect(jsonPath("$._embedded.podcasts[0]._links.self.href").isString())
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$._links.self.href").isString())
                .andExpect(jsonPath("$._links.first.href").isString())
                .andExpect(jsonPath("$._links.next.href").isString())
                .andExpect(jsonPath("$._links.last.href").isString());
        mockMvc.perform(get("/api/v1/podcasts?sort=nope").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void deletesAPodcastWithItsEpisodesAudioAndTokens() throws Exception {
        String slug = ApiTestClient.uniqueSlug("delete");
        api.createPodcast(slug);
        String token = ApiTestClient.bearer(api.createApiToken(slug));
        JsonNode episode = api.publishEpisode(slug, token, "{\"title\":\"Doomed\"}", new byte[] {1, 2, 3});
        long oid = api.audioOidOfEpisode(episode.get("id").asString());
        assertThat(api.largeObjectExists(oid)).isTrue();

        mockMvc.perform(delete("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(api.largeObjectExists(oid)).isFalse();
        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void routesTheEpisodesSlugUnambiguously() throws Exception {
        api.createPodcast("episodes");

        mockMvc.perform(get("/api/v1/podcasts/episodes").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("episodes"));
    }
}

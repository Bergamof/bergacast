package fr.bergadev.bergacast.episode;

import static fr.bergadev.bergacast.support.ApiTestClient.bearer;
import static fr.bergadev.bergacast.support.ApiTestClient.uploadRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.image.TestImages;
import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import fr.bergadev.bergacast.support.StoredObjects;
import fr.bergadev.bergacast.support.StoredObjects.StoredObject;
import fr.bergadev.bergacast.web.Sha256;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class EpisodeImageApiIntegrationTest {

    private static final String MEDIA_BASE_URL = ApiTestClient.MEDIA_BASE_URL;
    private static final byte[] AUDIO = {1, 2, 3, 4};
    private static final byte[] SMALL_PNG = TestImages.png(300, 200);
    private static final byte[] SMALL_JPEG = TestImages.jpeg(640, 640);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private StoredObjects storedObjects;

    private String admin;
    private String slug;
    private String episodeId;

    @BeforeEach
    void publishAnEpisode() throws Exception {
        admin = api.adminAuthorization();
        slug = ApiTestClient.uniqueSlug("episode-image");
        api.createPodcast(slug);
        episodeId = api.publishEpisode(slug, admin, "{\"title\":\"With image\"}", AUDIO)
                .get("id")
                .asString();
    }

    @Test
    void createsTheImageOfAnEpisode() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith(imagePath())))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + Sha256.hex(SMALL_PNG) + "\""))
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.width").value(300))
                .andExpect(jsonPath("$.height").value(200))
                .andExpect(jsonPath("$.sizeBytes").value(SMALL_PNG.length))
                .andExpect(jsonPath("$.sha256").value(Sha256.hex(SMALL_PNG)))
                .andExpect(jsonPath("$.url").value(imageUrl("png")))
                .andExpect(jsonPath("$._links.self.href").value(endsWith(imagePath())))
                .andExpect(jsonPath("$._links.episode.href").value(endsWith(episodePath())))
                .andExpect(jsonPath("$._links.content.href").value(imageUrl("png")));

        StoredObject stored = imageObject("png").orElseThrow();
        assertThat(stored.content()).isEqualTo(SMALL_PNG);
        assertThat(stored.contentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(stored.cacheControl()).isEqualTo("max-age=300, public");
    }

    @Test
    void replacesTheImageOfAnEpisode() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + Sha256.hex(SMALL_JPEG) + "\""))
                .andExpect(jsonPath("$.url").value(imageUrl("jpg")));

        assertThat(imageObject("jpg").orElseThrow().content()).isEqualTo(SMALL_JPEG);
        assertThat(imageObject("png")).as("image of the former format").isEmpty();
    }

    @Test
    void downscalesAnImageLargerThanTheApplePodcastsMaximum() throws Exception {
        MvcResult result = uploadImage(TestImages.png(4000, 2000), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(3000))
                .andExpect(jsonPath("$.height").value(1500))
                .andReturn();

        byte[] served = imageObject("png").orElseThrow().content();
        BufferedImage stored = TestImages.decode(served);
        assertThat(stored.getWidth()).isEqualTo(3000);
        assertThat(api.json(result).get("sha256").asString()).isEqualTo(Sha256.hex(served));
    }

    @Test
    void honoursIfMatchOnUpload() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE, "\"0123\"").andExpect(status().isPreconditionFailed());
        String etag = uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE, "\"stale\"")
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(imageObject("jpg")).as("image rejected by its precondition").isEmpty();
        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE, etag).andExpect(status().isOk());
    }

    @Test
    void rejectsAnUnsupportedMediaType() throws Exception {
        uploadImage(TestImages.encode(TestImages.gradient(10, 10, BufferedImage.TYPE_INT_RGB), "gif"), "image/gif")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAnImageLargerThanTheUploadLimit() throws Exception {
        uploadImage(tooLargeImage(), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void checksTheEpisodeAndIfMatchBeforeReadingTheContent() throws Exception {
        mockMvc.perform(put("/api/v1/{slug}/episodes/{id}/image", slug, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(tooLargeImage()))
                .andExpect(status().isNotFound());
        uploadImage(tooLargeImage(), MediaType.IMAGE_PNG_VALUE, "\"stale\"").andExpect(status().isPreconditionFailed());
    }

    @Test
    void rejectsAnImageWhoseContentDoesNotMatchItsType() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_JPEG_VALUE)
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("$"))
                .andExpect(jsonPath("$.errors[0].message").value("is a PNG image but was declared as image/jpeg"));
    }

    @Test
    void rejectsCorruptContentAndDecompressionBombs() throws Exception {
        uploadImage(new byte[] {1, 2, 3}, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("is not a readable PNG image"));
        uploadImage(TestImages.pngHeaderOnly(20_000, 20_000), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        assertThat(storedObjects.keys(episodeFolder())).containsExactly(audioKey());
    }

    @Test
    void isReservedToAdministrators() throws Exception {
        String podcastToken = bearer(api.createApiToken(slug));

        mockMvc.perform(put(imagePath()).contentType(MediaType.IMAGE_PNG).content(SMALL_PNG))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")));
        mockMvc.perform(put(imagePath())
                        .header(HttpHeaders.AUTHORIZATION, podcastToken)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(SMALL_PNG))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(imagePath()).header(HttpHeaders.AUTHORIZATION, podcastToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(imagePath()).header(HttpHeaders.AUTHORIZATION, podcastToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsTheImageMetadataWithConditionalRequests() throws Exception {
        mockMvc.perform(get(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE).andExpect(status().isCreated());
        String etag = "\"" + Sha256.hex(SMALL_JPEG) + "\"";

        mockMvc.perform(get(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(jsonPath("$.width").value(640))
                .andExpect(jsonPath("$._links.content.href").value(imageUrl("jpg")));
        mockMvc.perform(get(imagePath())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void deletesTheImage() throws Exception {
        mockMvc.perform(delete(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        mockMvc.perform(delete(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        assertThat(imageObject("png")).isEmpty();
        mockMvc.perform(get(episodePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.image").isEmpty())
                .andExpect(jsonPath("$._links.image.href").value(endsWith(imagePath())));
    }

    @Test
    void answers404ForAnUnknownEpisodeOrAnEpisodeOfAnotherPodcast() throws Exception {
        String otherSlug = ApiTestClient.uniqueSlug("episode-image-other");
        api.createPodcast(otherSlug);
        String[] paths = {
            "/api/v1/" + slug + "/episodes/" + UUID.randomUUID() + "/image",
            "/api/v1/" + otherSlug + "/episodes/" + episodeId + "/image",
            "/api/v1/no-such-podcast/episodes/" + episodeId + "/image"
        };

        for (String path : paths) {
            mockMvc.perform(put(path)
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.IMAGE_PNG)
                            .content(SMALL_PNG))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());
            mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNotFound());
        }
        assertThat(storedObjects.keys(episodeFolder())).containsExactly(audioKey());
    }

    @Test
    void changesTheEpisodeRepresentationAndEntityTag() throws Exception {
        String before = episodeEntityTag();
        mockMvc.perform(get(episodePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.image").isEmpty())
                .andExpect(jsonPath("$._links.image.href").value(endsWith(imagePath())));

        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        String afterUpload = episodeEntityTag();
        assertThat(afterUpload).isNotEqualTo(before);
        mockMvc.perform(get(episodePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.image.mimeType").value("image/png"))
                .andExpect(jsonPath("$.image.width").value(300))
                .andExpect(jsonPath("$.image.sha256").value(Sha256.hex(SMALL_PNG)))
                .andExpect(jsonPath("$.image.url").value(imageUrl("png")));
        mockMvc.perform(get(episodePath())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_NONE_MATCH, before))
                .andExpect(status().isOk());

        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE).andExpect(status().isOk());
        String afterReplacement = episodeEntityTag();
        assertThat(afterReplacement).isNotEqualTo(afterUpload);

        mockMvc.perform(delete(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        assertThat(episodeEntityTag()).isNotEqualTo(afterReplacement);
    }

    @Test
    void listsEpisodesWithTheirImage() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());
        String withoutImage = api.publishEpisode(slug, admin, "{\"title\":\"Plain\"}", AUDIO)
                .get("id")
                .asString();

        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.episodes[?(@.id == '" + episodeId + "')].image.width")
                        .value(300))
                .andExpect(jsonPath("$._embedded.episodes[?(@.id == '" + withoutImage + "')].image")
                        .value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void advertisesTheImageInTheFeedItemOnly() throws Exception {
        api.publishEpisode(slug, admin, "{\"title\":\"Plain\"}", AUDIO);
        assertThat(feed()).doesNotContain("<itunes:image");

        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        assertThat(feed())
                .contains("<itunes:image href=\"" + imageUrl("png") + "\"")
                .containsOnlyOnce("<itunes:image");
        assertThat(feed().indexOf("<itunes:image"))
                .as("in an item, not the channel")
                .isGreaterThan(feed().indexOf("<item>"));

        mockMvc.perform(delete(imagePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(feed()).doesNotContain("<itunes:image");
    }

    @Test
    void deletesTheImageObjectWithItsEpisode() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        mockMvc.perform(delete(episodePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(storedObjects.keys(episodeFolder())).isEmpty();
    }

    @Test
    void deletesTheImageObjectWithItsPodcast() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(storedObjects.keys(MediaKeys.podcastFolder(slug))).isEmpty();
    }

    @Test
    void publishesAnEpisodeWithItsImage() throws Exception {
        String token = bearer(api.createApiToken(slug));

        MvcResult result = mockMvc.perform(withImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE, "{\"title\":\"One go\"}")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.image.width").value(640))
                .andExpect(jsonPath("$._links.image.href").exists())
                .andReturn();

        JsonNode episode = api.json(result);
        String id = episode.get("id").asString();
        assertThat(episode.get("image").get("url").asString())
                .isEqualTo(MEDIA_BASE_URL + slug + "/episodes/" + id + "/image.jpg");
        StoredObject stored = storedObjects.get(MediaKeys.episodeImage(slug, UUID.fromString(id), "jpg"));
        assertThat(stored.content()).isEqualTo(SMALL_JPEG);
        assertThat(stored.contentType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
        assertThat(feed())
                .contains("<itunes:image href=\""
                        + episode.get("image").get("url").asString() + "\"");
    }

    @Test
    void publishesAnEpisodeWithoutImageWhenThePartIsMissingOrEmpty() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("image", "none.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        mockMvc.perform(uploadRequest(slug, "{\"title\":\"Empty\"}", AUDIO, "audio/mpeg")
                        .file(empty)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image").isEmpty());
    }

    @Test
    void publishesNothingWhenTheImagePartIsRejected() throws Exception {
        int audioObjects = storedObjects.keys(MediaKeys.podcastFolder(slug)).size();

        mockMvc.perform(withImage(new byte[] {1, 2, 3}, MediaType.IMAGE_PNG_VALUE, "{\"title\":\"Bad\"}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("image"));
        mockMvc.perform(withImage(SMALL_PNG, "image/gif", "{\"title\":\"Gif\"}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(withImage(tooLargeImage(), MediaType.IMAGE_PNG_VALUE, "{\"title\":\"Huge\"}")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isContentTooLarge());

        assertThat(storedObjects.keys(MediaKeys.podcastFolder(slug))).hasSize(audioObjects);
        mockMvc.perform(get("/api/v1/{slug}/episodes", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void replaysAnImagePublicationWithTheSameIdempotencyKey() throws Exception {
        String key = "image-" + UUID.randomUUID();
        MvcResult first = mockMvc.perform(withImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE, "{\"title\":\"Once\"}")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andReturn();
        int objects = storedObjects.keys(MediaKeys.podcastFolder(slug)).size();

        MvcResult retry = mockMvc.perform(withImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE, "{\"title\":\"Once\"}")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(api.json(retry).get("id")).isEqualTo(api.json(first).get("id"));
        assertThat(api.json(retry).get("image").get("mimeType").asString()).isEqualTo("image/png");
        assertThat(storedObjects.keys(MediaKeys.podcastFolder(slug))).hasSize(objects);
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder withImage(
            byte[] image, String imageContentType, String metadataJson) {
        return uploadRequest(slug, metadataJson, AUDIO, "audio/mpeg")
                .file(new MockMultipartFile("image", "cover", imageContentType, image));
    }

    private ResultActions uploadImage(byte[] content, String contentType) throws Exception {
        return uploadImage(content, contentType, null);
    }

    private ResultActions uploadImage(byte[] content, String contentType, @Nullable String ifMatch) throws Exception {
        var request = put(imagePath())
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(contentType)
                .content(content);
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return mockMvc.perform(request);
    }

    private static byte[] tooLargeImage() {
        return new byte[(int) DataSize.ofMegabytes(20).toBytes() + 1];
    }

    private String episodePath() {
        return "/api/v1/" + slug + "/episodes/" + episodeId;
    }

    private String imagePath() {
        return episodePath() + "/image";
    }

    private String imageUrl(String fileExtension) {
        return MEDIA_BASE_URL + MediaKeys.episodeImage(slug, UUID.fromString(episodeId), fileExtension);
    }

    private String episodeFolder() {
        return MediaKeys.podcastFolder(slug) + "episodes/" + episodeId + "/";
    }

    private String audioKey() {
        return episodeFolder() + "audio.mp3";
    }

    private Optional<StoredObject> imageObject(String fileExtension) {
        return storedObjects.find(MediaKeys.episodeImage(slug, UUID.fromString(episodeId), fileExtension));
    }

    private String feed() {
        return new String(storedObjects.get(MediaKeys.feed(slug)).content(), StandardCharsets.UTF_8);
    }

    private String episodeEntityTag() throws Exception {
        return mockMvc.perform(get(episodePath()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
    }
}

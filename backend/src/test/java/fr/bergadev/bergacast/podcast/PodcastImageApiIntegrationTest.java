package fr.bergadev.bergacast.podcast;

import static fr.bergadev.bergacast.support.ApiTestClient.bearer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class PodcastImageApiIntegrationTest {

    private static final String MEDIA_BASE_URL = ApiTestClient.MEDIA_BASE_URL;
    private static final byte[] SMALL_PNG = TestImages.png(300, 200);
    private static final byte[] SMALL_JPEG = TestImages.jpeg(640, 640);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private StoredObjects storedObjects;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String admin;
    private String slug;
    private UUID podcastId;

    @BeforeEach
    void createPodcast() throws Exception {
        admin = api.adminAuthorization();
        slug = ApiTestClient.uniqueSlug("image");
        podcastId = UUID.fromString(api.createPodcast(slug).get("id").asString());
    }

    @Test
    void createsTheImageOfAPodcast() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/podcasts/" + slug + "/image")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + Sha256.hex(SMALL_PNG) + "\""))
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.width").value(300))
                .andExpect(jsonPath("$.height").value(200))
                .andExpect(jsonPath("$.sizeBytes").value(SMALL_PNG.length))
                .andExpect(jsonPath("$.sha256").value(Sha256.hex(SMALL_PNG)))
                .andExpect(jsonPath("$.url").value(MEDIA_BASE_URL + slug + "/image.png"))
                .andExpect(jsonPath("$._links.self.href").value(endsWith("/api/v1/podcasts/" + slug + "/image")))
                .andExpect(jsonPath("$._links.podcast.href").value(endsWith("/api/v1/podcasts/" + slug)))
                .andExpect(jsonPath("$._links.content.href").value(MEDIA_BASE_URL + slug + "/image.png"));

        StoredObject stored = imageObject("png").orElseThrow();
        assertThat(stored.content()).isEqualTo(SMALL_PNG);
        assertThat(stored.contentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(stored.cacheControl()).isEqualTo("max-age=300, public");
    }

    @Test
    void replacesTheImageOfAPodcast() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + Sha256.hex(SMALL_JPEG) + "\""))
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.url").value(MEDIA_BASE_URL + slug + "/image.jpg"));

        StoredObject replacement = imageObject("jpg").orElseThrow();
        assertThat(replacement.content()).isEqualTo(SMALL_JPEG);
        assertThat(replacement.contentType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
        assertThat(imageObject("png")).as("image of the former format").isEmpty();
    }

    @Test
    void overwritesTheObjectWhenTheFormatStaysTheSame() throws Exception {
        byte[] otherPng = TestImages.png(320, 320);
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        uploadImage(otherPng, MediaType.IMAGE_PNG_VALUE).andExpect(status().isOk());

        assertThat(imageObject("png").orElseThrow().content()).isEqualTo(otherPng);
    }

    @Test
    void downscalesAnImageLargerThanTheApplePodcastsMaximum() throws Exception {
        byte[] large = TestImages.png(4000, 2000);

        MvcResult result = uploadImage(large, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(3000))
                .andExpect(jsonPath("$.height").value(1500))
                .andReturn();

        byte[] served = imageObject("png").orElseThrow().content();
        BufferedImage stored = TestImages.decode(served);
        assertThat(stored.getWidth()).isEqualTo(3000);
        assertThat(stored.getHeight()).isEqualTo(1500);
        JsonNode image = api.json(result);
        assertThat(image.get("sizeBytes").asLong()).isEqualTo(served.length);
        assertThat(image.get("sha256").asString()).isEqualTo(Sha256.hex(served));
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
        byte[] tooLarge = new byte[(int) DataSize.ofMegabytes(20).toBytes() + 1];

        uploadImage(tooLarge, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void checksThePodcastAndIfMatchBeforeReadingTheContent() throws Exception {
        byte[] tooLarge = new byte[(int) DataSize.ofMegabytes(20).toBytes() + 1];

        mockMvc.perform(put("/api/v1/podcasts/{slug}/image", "no-such-podcast")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(tooLarge))
                .andExpect(status().isNotFound());
        uploadImage(tooLarge, MediaType.IMAGE_PNG_VALUE, "\"stale\"").andExpect(status().isPreconditionFailed());
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());
        uploadImage(tooLarge, MediaType.IMAGE_PNG_VALUE, "\"stale\"").andExpect(status().isPreconditionFailed());
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("more than the 50000000 allowed")));
        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        assertThat(storedObjects.keys(MediaKeys.podcastFolder(slug))).isEmpty();
    }

    @Test
    void isReservedToAdministrators() throws Exception {
        String podcastToken = bearer(api.createApiToken(slug));

        mockMvc.perform(put("/api/v1/podcasts/{slug}/image", slug)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(SMALL_PNG))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")));
        mockMvc.perform(put("/api/v1/podcasts/{slug}/image", slug)
                        .header(HttpHeaders.AUTHORIZATION, podcastToken)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(SMALL_PNG))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, podcastToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, podcastToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsTheImageMetadataWithConditionalRequests() throws Exception {
        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE).andExpect(status().isCreated());
        String etag = "\"" + Sha256.hex(SMALL_JPEG) + "\"";

        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(jsonPath("$.width").value(640))
                .andExpect(jsonPath("$._links.content.href").value(MEDIA_BASE_URL + slug + "/image.jpg"));
        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void deletesTheImage() throws Exception {
        mockMvc.perform(delete("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        assertThat(imageObject("png")).isEmpty();
        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.image").isEmpty())
                .andExpect(jsonPath("$._links.image.href").value(endsWith("/api/v1/podcasts/" + slug + "/image")));
    }

    @Test
    void answers404ForAnUnknownPodcast() throws Exception {
        mockMvc.perform(put("/api/v1/podcasts/{slug}/image", "no-such-podcast")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.IMAGE_PNG)
                        .content(SMALL_PNG))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/podcasts/{slug}/image", "no-such-podcast")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/podcasts/{slug}/image", "no-such-podcast")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        assertThat(storedObjects.keys(MediaKeys.podcastFolder("no-such-podcast")))
                .isEmpty();
    }

    @Test
    void changesThePodcastRepresentationAndEntityTag() throws Exception {
        String before = podcastEntityTag();

        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());

        String afterUpload = podcastEntityTag();
        assertThat(afterUpload).isNotEqualTo(before);
        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.image.mimeType").value("image/png"))
                .andExpect(jsonPath("$.image.width").value(300))
                .andExpect(jsonPath("$.image.height").value(200))
                .andExpect(jsonPath("$.image.sizeBytes").value(SMALL_PNG.length))
                .andExpect(jsonPath("$.image.sha256").value(Sha256.hex(SMALL_PNG)))
                .andExpect(jsonPath("$.image.url").value(MEDIA_BASE_URL + slug + "/image.png"))
                .andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$._links.image.href").value(endsWith("/api/v1/podcasts/" + slug + "/image")));
        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.IF_NONE_MATCH, before))
                .andExpect(status().isOk());

        uploadImage(SMALL_JPEG, MediaType.IMAGE_JPEG_VALUE).andExpect(status().isOk());
        String afterReplacement = podcastEntityTag();
        assertThat(afterReplacement).isNotEqualTo(afterUpload);

        mockMvc.perform(delete("/api/v1/podcasts/{slug}/image", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        assertThat(podcastEntityTag()).isNotEqualTo(afterReplacement);
    }

    @Test
    void listsPodcastsWithTheirImageMetadata() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());
        String withoutImage = ApiTestClient.uniqueSlug("image-none");
        api.createPodcast(withoutImage);

        mockMvc.perform(get("/api/v1/podcasts")
                        .param("sort", "createdAt,desc")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.podcasts[?(@.slug == '" + slug + "')].image.width")
                        .value(300))
                .andExpect(jsonPath("$._embedded.podcasts[?(@.slug == '" + withoutImage + "')].image")
                        .value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void advertisesTheUploadedImageInTheFeedInsteadOfTheExternalUrl() throws Exception {
        String external = ApiTestClient.uniqueSlug("image-external");
        api.createPodcast(external, ",\"imageUrl\":\"https://cdn.example.test/cover.jpg\"");

        assertThat(feedOf(external)).contains("<itunes:image href=\"https://cdn.example.test/cover.jpg\"");
        assertThat(feedOf(slug)).doesNotContain("<itunes:image");

        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE, null, external).andExpect(status().isCreated());

        assertThat(feedOf(external))
                .contains("<itunes:image href=\"" + MEDIA_BASE_URL + external + "/image.png\"")
                .doesNotContain("cdn.example.test");
        mockMvc.perform(get("/api/v1/podcasts/{slug}", external).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.example.test/cover.jpg"));

        mockMvc.perform(delete("/api/v1/podcasts/{slug}/image", external).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(feedOf(external)).contains("<itunes:image href=\"https://cdn.example.test/cover.jpg\"");
    }

    @Test
    void deletesTheImageWithItsPodcast() throws Exception {
        uploadImage(SMALL_PNG, MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());
        assertThat(imageRows()).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        assertThat(imageRows()).isZero();
        assertThat(imageObject("png")).isEmpty();
    }

    private ResultActions uploadImage(byte[] content, String contentType) throws Exception {
        return uploadImage(content, contentType, null);
    }

    private ResultActions uploadImage(byte[] content, String contentType, @Nullable String ifMatch) throws Exception {
        return uploadImage(content, contentType, ifMatch, slug);
    }

    private ResultActions uploadImage(byte[] content, String contentType, @Nullable String ifMatch, String podcastSlug)
            throws Exception {
        var request = put("/api/v1/podcasts/{slug}/image", podcastSlug)
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(contentType)
                .content(content);
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return mockMvc.perform(request);
    }

    private Optional<StoredObject> imageObject(String fileExtension) {
        return storedObjects.find(MediaKeys.podcastImage(slug, fileExtension));
    }

    private String feedOf(String podcastSlug) {
        return new String(storedObjects.get(MediaKeys.feed(podcastSlug)).content(), StandardCharsets.UTF_8);
    }

    private String podcastEntityTag() throws Exception {
        return mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
    }

    private Integer imageRows() {
        return jdbcTemplate.queryForObject(
                "select count(*) from podcast_image where podcast_id = ?", Integer.class, podcastId);
    }
}

package fr.bergadev.bergacast.episode;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** The audio file may be exactly as large as {@code BERGACAST_MAX_UPLOAD_SIZE}, not larger. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.servlet.multipart.max-file-size=" + UploadSizeLimitIntegrationTest.LIMIT_BYTES + "B")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UploadSizeLimitIntegrationTest {

    static final int LIMIT_BYTES = 64 * 1024;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String admin;
    private String slug;

    @BeforeEach
    void createPodcast() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
        JsonNode token = client.post()
                .uri("/api/v1/access-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"" + ApiTestClient.ADMIN_USERNAME + "\",\"password\":\""
                        + ApiTestClient.ADMIN_PASSWORD + "\"}")
                .retrieve()
                .body(JsonNode.class);
        admin = ApiTestClient.bearer(token.get("accessToken").asString());
        slug = ApiTestClient.uniqueSlug("limit");
        client.post()
                .uri("/api/v1/podcasts")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"slug\":\"" + slug + "\",\"title\":\"t\",\"description\":\"d\",\"language\":\"en\"}")
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void acceptsAFileExactlyAtTheLimit() {
        assertThat(upload(LIMIT_BYTES)).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void rejectsAFileOverTheLimit() {
        assertThat(upload(LIMIT_BYTES + 1)).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    private HttpStatusCode upload(int size) {
        HttpHeaders audioHeaders = new HttpHeaders();
        audioHeaders.setContentType(MediaType.parseMediaType("audio/mpeg"));
        MultiValueMap<String, HttpEntity<?>> multipart = new LinkedMultiValueMap<>();
        multipart.add("metadata", new HttpEntity<>("{\"title\":\"Sized\"}"));
        multipart.add(
                "audio",
                new HttpEntity<>(
                        new ByteArrayResource(new byte[size]) {
                            @Override
                            public String getFilename() {
                                return "sized.mp3";
                            }
                        },
                        audioHeaders));
        return client.post()
                .uri("/api/v1/{slug}/episodes", slug)
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }
}

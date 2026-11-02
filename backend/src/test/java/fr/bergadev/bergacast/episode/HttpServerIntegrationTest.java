package fr.bergadev.bergacast.episode;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.StoredObjects;
import fr.bergadev.bergacast.support.TestcontainersConfiguration;
import fr.bergadev.bergacast.web.JsonRequestSizeFilter;
import fr.bergadev.bergacast.web.Sha256;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Exercises behaviours MockMvc cannot observe: Tomcat multipart streaming of large uploads, request bodies sent
 * without Content-Length (chunked).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StoredObjects.class})
class HttpServerIntegrationTest {

    private static final int AUDIO_SIZE = 3 * 1024 * 1024 + 17;

    @LocalServerPort
    private int port;

    @Autowired
    private StoredObjects storedObjects;

    private RestClient client;

    @BeforeEach
    void createClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void streamsALargeUploadToTheObjectStorage() {
        byte[] audio = new byte[AUDIO_SIZE];
        new Random(42).nextBytes(audio);
        String admin = ApiTestClient.bearer(login());
        String slug = ApiTestClient.uniqueSlug("stream");
        client.post()
                .uri("/api/v1/podcasts")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"slug\":\"" + slug + "\",\"title\":\"t\",\"description\":\"d\",\"language\":\"en\"}")
                .retrieve()
                .toBodilessEntity();

        MultiValueMap<String, HttpEntity<?>> multipart = new LinkedMultiValueMap<>();
        multipart.add("metadata", part("{\"title\":\"Big\"}", MediaType.APPLICATION_JSON));
        multipart.add(
                "audio",
                part(
                        new ByteArrayResource(audio) {
                            @Override
                            public String getFilename() {
                                return "big.mp3";
                            }
                        },
                        MediaType.parseMediaType("audio/mpeg")));
        ResponseEntity<JsonNode> created = client.post()
                .uri("/api/v1/{slug}/episodes", slug)
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode episode = created.getBody();
        assertThat(episode).isNotNull();
        assertThat(episode.get("audio").get("sizeBytes").asLong()).isEqualTo(AUDIO_SIZE);
        assertThat(episode.get("audio").get("sha256").asString()).isEqualTo(Sha256.hex(audio));

        assertThat(storedObjects.get(ApiTestClient.audioKeyOf(episode)).content())
                .isEqualTo(audio);
    }

    private static HttpEntity<?> part(Object body, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void refusesAChunkedJsonBodyAboveTheLimitWith413() throws Exception {
        String slug = ApiTestClient.uniqueSlug("chunked");
        byte[] body = ("{\"slug\":\"" + slug + "\",\"title\":\"t\",\"description\":\""
                        + "d".repeat(Math.toIntExact(JsonRequestSizeFilter.MAX_JSON_BODY.toBytes()))
                        + "\",\"language\":\"en\"}")
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/podcasts"))
                .header(HttpHeaders.AUTHORIZATION, ApiTestClient.bearer(login()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // An input stream of unknown length is sent chunked, without Content-Length.
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
            assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                    .hasValue(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        }
    }

    private String login() {
        JsonNode token = client.post()
                .uri("/api/v1/access-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"" + ApiTestClient.ADMIN_USERNAME + "\",\"password\":\""
                        + ApiTestClient.ADMIN_PASSWORD + "\"}")
                .retrieve()
                .body(JsonNode.class);
        return token.get("accessToken").asString();
    }
}

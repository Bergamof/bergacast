package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.bergadev.bergacast.support.IntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Guards the completeness of the OpenAPI document, which external tools and Swagger UI users rely on. */
@IntegrationTest
class OpenApiDocumentationIntegrationTest {

    /** Generic Spring HATEOAS schema, replaced by springdoc after every customizer. */
    private static final Set<String> UNDOCUMENTED_SCHEMAS = Set.of("Link");

    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "getApiRoot", "createAccessToken", "getPodcastFeed", "downloadEpisodeAudio", "downloadEpisodeAudio_1");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    private JsonNode document;

    @BeforeEach
    void readDocument() throws Exception {
        byte[] content = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        document = jsonMapper.readTree(content);
    }

    @Test
    void describesEveryPropertyOfTheSchemas() {
        List<String> undescribed = new ArrayList<>();
        document.get("components").get("schemas").properties().stream()
                .filter(schema -> !UNDOCUMENTED_SCHEMAS.contains(schema.getKey()))
                .forEach(schema -> collectUndescribedProperties(schema.getValue(), schema.getKey(), undescribed));

        assertThat(undescribed).isEmpty();
    }

    @Test
    void describesEveryOperationAndParameter() {
        List<String> undescribed = new ArrayList<>();
        operations().forEach(operation -> {
            String id = operation.get("operationId").asString();
            if (isBlank(operation, "summary") || isBlank(operation, "description")) {
                undescribed.add(id);
            }
            parameters(operation)
                    .filter(parameter -> isBlank(parameter, "description"))
                    .forEach(parameter ->
                            undescribed.add(id + " " + parameter.get("name").asString()));
        });

        assertThat(undescribed).isEmpty();
    }

    @Test
    void documentsAuthenticationErrorsOfSecuredOperationsOnly() {
        operations().forEach(operation -> {
            String id = operation.get("operationId").asString();
            JsonNode responses = operation.get("responses");
            if (PUBLIC_OPERATIONS.contains(id)) {
                assertThat(operation.has("security")).as(id).isFalse();
                assertThat(responses.has("403")).as(id).isFalse();
                assertThat(responses.path("401").path("headers").has("WWW-Authenticate"))
                        .as(id)
                        .isFalse();
            } else {
                assertThat(operation.get("security")).as(id).isNotEmpty();
                assertThat(responses.get("401").get("headers").has("WWW-Authenticate"))
                        .as(id)
                        .isTrue();
                assertThat(responses.get("403").get("description").asString())
                        .as(id)
                        .isNotBlank();
            }
        });
    }

    @Test
    void documentsTheLocationOfCreatedResources() {
        Map<String, JsonNode> created = new HashMap<>();
        operations()
                .filter(operation -> operation.get("responses").has("201"))
                .forEach(operation -> created.put(
                        operation.get("operationId").asString(),
                        operation.get("responses").get("201").get("headers")));

        assertThat(created).containsOnlyKeys("createPodcast", "publishEpisode", "createApiToken", "createAdminUser");
        assertThat(created.values())
                .allSatisfy(headers -> assertThat(headers.has("Location")).isTrue());
    }

    @Test
    void documentsTheEntityTagOfVersionedRepresentations() {
        assertThat(responseHeaders("createPodcast", "201")).contains("ETag");
        assertThat(responseHeaders("getPodcast", "200")).contains("ETag");
        assertThat(responseHeaders("getPodcast", "304")).contains("ETag");
        assertThat(responseHeaders("updatePodcast", "200")).contains("ETag");
        assertThat(responseHeaders("publishEpisode", "201")).contains("ETag");
        assertThat(responseHeaders("getEpisode", "200")).contains("ETag");
        assertThat(responseHeaders("updateEpisode", "200")).contains("ETag");
        assertThat(responseHeaders("getPodcastFeed", "200")).contains("ETag");
        assertThat(responseHeaders("downloadEpisodeAudio", "206")).contains("ETag", "Content-Range");
        assertThat(responseHeaders("createApiToken", "201")).doesNotContain("ETag");
    }

    @Test
    void documentsConditionalRequestHeaders() {
        assertThat(parameterNames("getEpisode")).contains("If-None-Match");
        assertThat(parameterNames("getPodcastFeed")).contains("If-None-Match");
        assertThat(parameterNames("updateEpisode")).contains("If-Match");
        assertThat(parameterNames("createPodcast")).doesNotContain("If-None-Match", "If-Match");
    }

    @Test
    void documentsTheEpisodePublicationWithExamples() {
        JsonNode publish = operation("publishEpisode");
        JsonNode multipart = publish.get("requestBody").get("content").get("multipart/form-data");

        assertThat(multipart
                        .get("examples")
                        .get("scheduled")
                        .get("value")
                        .get("metadata")
                        .has("publicationDate"))
                .isTrue();
        assertThat(publish.get("description").asString()).contains("Idempotency-Key", "curl", "text/plain");
        JsonNode metadata = document.get("components").get("schemas").get("EpisodeInput");
        assertThat(metadata.get("properties").get("episodeType").get("default").asString())
                .isEqualTo("FULL");
        assertThat(metadata.get("properties").get("title").has("example")).isTrue();
    }

    private static void collectUndescribedProperties(JsonNode schema, String path, List<String> undescribed) {
        schema.path("properties").properties().forEach(property -> {
            String propertyPath = path + "." + property.getKey();
            if (isBlank(property.getValue(), "description")) {
                undescribed.add(propertyPath);
            }
            collectUndescribedProperties(property.getValue(), propertyPath, undescribed);
        });
        if (schema.has("items")) {
            collectUndescribedProperties(schema.get("items"), path + "[]", undescribed);
        }
    }

    private Stream<JsonNode> operations() {
        return document.get("paths").valueStream().flatMap(JsonNode::valueStream);
    }

    private JsonNode operation(String operationId) {
        return operations()
                .filter(operation ->
                        operationId.equals(operation.get("operationId").asString()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> responseHeaders(String operationId, String status) {
        return operation(operationId).get("responses").get(status).path("headers").propertyNames().stream()
                .toList();
    }

    private List<String> parameterNames(String operationId) {
        return parameters(operation(operationId))
                .map(parameter -> parameter.get("name").asString())
                .toList();
    }

    private static Stream<JsonNode> parameters(JsonNode operation) {
        return operation.path("parameters").valueStream();
    }

    private static boolean isBlank(JsonNode node, String field) {
        return node.path(field).asString("").isBlank();
    }
}

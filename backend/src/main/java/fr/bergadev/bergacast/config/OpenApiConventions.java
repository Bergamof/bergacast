package fr.bergadev.bergacast.config;

import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * Documents the conventions shared by every operation of the API, so that controllers only describe what is specific
 * to them: authentication errors, {@code 404}, {@code Location} and {@code ETag} headers, conditional request
 * headers, common parameters, HAL members and Problem Details error bodies.
 */
final class OpenApiConventions implements OpenApiCustomizer {

    static final String ETAG_HEADER_REF = "#/components/headers/" + HttpHeaders.ETAG;

    private static final String PROBLEM_SCHEMA = "Problem";

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM_SCHEMA;
    private static final String LOCATION_HEADER_REF = "#/components/headers/" + HttpHeaders.LOCATION;
    private static final String WWW_AUTHENTICATE_HEADER_REF = "#/components/headers/" + HttpHeaders.WWW_AUTHENTICATE;
    private static final int FIRST_ERROR_STATUS = 400;
    private static final String CREATED = "201";
    private static final String NOT_MODIFIED = "304";
    private static final String UNAUTHORIZED = "401";
    private static final String FORBIDDEN = "403";
    private static final String NOT_FOUND = "404";
    private static final String DEFAULT_RESPONSE = "default";
    private static final String PATH = "path";
    private static final String HEADER = "header";
    private static final String QUERY = "query";
    private static final String HAL_LINKS = "_links";
    private static final String HAL_EMBEDDED = "_embedded";
    private static final String PAGE = "page";
    private static final String OTHER_PODCAST_TOKEN = "The API token belongs to another podcast";
    private static final String NOT_AN_ADMINISTRATOR =
            "Not an administrator: a podcast API token only grants access to the episodes of its podcast";

    private static final Map<String, String> PATH_PARAMETER_DESCRIPTIONS = Map.of(
            "slug", "Slug of the podcast: its URL identifier, as in its feed URL `/{slug}.xml`.",
            "episodeId", "Identifier of the episode (its `id`, not its `guid`).",
            "tokenId", "Identifier of the API token.",
            "adminUserId", "Identifier of the administrator.");
    private static final Map<String, String> PAGE_PROPERTY_DESCRIPTIONS = Map.of(
            "size", "Requested number of items per page.",
            "totalElements", "Total number of items.",
            "totalPages", "Total number of pages.",
            "number", "Zero-based index of this page.");

    private final int maxPageSize;

    OpenApiConventions(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Components components = Objects.requireNonNull(openApi.getComponents());
        addSharedComponents(components);
        operations(openApi).forEach(operation -> {
            documentAuthenticationErrors(operation);
            documentNotFound(operation);
            documentLocation(operation.getResponses());
            documentConditionalRequestHeaders(operation);
            describeCommonParameters(operation);
            documentProblemDetails(operation.getResponses());
        });
        components.getSchemas().forEach((name, schema) -> describeHalMembers(name, schema));
    }

    private static Stream<Operation> operations(OpenAPI openApi) {
        return openApi.getPaths().values().stream().flatMap(pathItem -> pathItem.readOperations().stream());
    }

    private static void addSharedComponents(Components components) {
        components
                .addSchemas(PROBLEM_SCHEMA, problemSchema())
                .addHeaders(
                        HttpHeaders.ETAG,
                        new Header()
                                .description("Strong entity tag of the representation: send it back in If-None-Match "
                                        + "to revalidate a cached copy, or in If-Match to update safely.")
                                .schema(new StringSchema().example("\"3\"")))
                .addHeaders(
                        HttpHeaders.LOCATION,
                        new Header()
                                .description("Absolute URL of the created resource.")
                                .schema(new StringSchema().format("uri")))
                .addHeaders(
                        HttpHeaders.WWW_AUTHENTICATE,
                        new Header()
                                .description("Bearer authentication challenge (RFC 6750).")
                                .schema(new StringSchema().example("Bearer")));
    }

    /** Secured operations answer 401 without a valid token, and 403 when the token does not grant access. */
    private static void documentAuthenticationErrors(Operation operation) {
        if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            return;
        }
        boolean podcastTokenAccepted = operation.getSecurity().stream()
                .anyMatch(requirement -> requirement.containsKey(SecuritySchemes.PODCAST_TOKEN));
        ApiResponses responses = operation.getResponses();
        responses.computeIfAbsent(
                UNAUTHORIZED,
                status -> new ApiResponse()
                        .description("Missing, invalid, expired or revoked bearer token")
                        .addHeaderObject(HttpHeaders.WWW_AUTHENTICATE, new Header().$ref(WWW_AUTHENTICATE_HEADER_REF)));
        responses.computeIfAbsent(
                FORBIDDEN,
                status -> new ApiResponse()
                        .description(podcastTokenAccepted ? OTHER_PODCAST_TOKEN : NOT_AN_ADMINISTRATOR));
    }

    private static void documentNotFound(Operation operation) {
        if (parameters(operation).anyMatch(parameter -> PATH.equals(parameter.getIn()))) {
            operation
                    .getResponses()
                    .computeIfAbsent(
                            NOT_FOUND,
                            status -> new ApiResponse().description("No such podcast, or no such resource in it"));
        }
    }

    private static void documentLocation(ApiResponses responses) {
        ApiResponse created = responses.get(CREATED);
        if (created != null) {
            created.addHeaderObject(HttpHeaders.LOCATION, new Header().$ref(LOCATION_HEADER_REF));
        }
    }

    private static void documentConditionalRequestHeaders(Operation operation) {
        parameters(operation)
                .filter(parameter ->
                        HEADER.equals(parameter.getIn()) && HttpHeaders.IF_MATCH.equalsIgnoreCase(parameter.getName()))
                .forEach(ifMatch -> ifMatch.setDescription("ETag of the representation being modified: the update "
                        + "is refused with 412 if the resource has changed since (optimistic locking). Optional."));
        boolean revalidable = operation.getResponses().containsKey(NOT_MODIFIED);
        boolean declared = parameters(operation)
                .anyMatch(parameter -> HttpHeaders.IF_NONE_MATCH.equalsIgnoreCase(parameter.getName()));
        if (revalidable && !declared) {
            operation.addParametersItem(new HeaderParameter()
                    .name(HttpHeaders.IF_NONE_MATCH)
                    .description("ETag of a cached representation: 304 Not Modified is returned while it is current.")
                    .schema(new StringSchema()));
        }
    }

    private void describeCommonParameters(Operation operation) {
        parameters(operation).forEach(parameter -> {
            if (PATH.equals(parameter.getIn()) && !StringUtils.hasText(parameter.getDescription())) {
                parameter.setDescription(PATH_PARAMETER_DESCRIPTIONS.get(parameter.getName()));
            } else if (QUERY.equals(parameter.getIn()) && "size".equals(parameter.getName())) {
                parameter.setDescription(
                        "Number of items per page, at most " + maxPageSize + " (larger values are lowered).");
            }
        });
    }

    /** Documents every error response, and a default one, as Problem Details. */
    private static void documentProblemDetails(ApiResponses responses) {
        responses.forEach((status, response) -> {
            if (isError(status)) {
                response.setContent(problemContent());
            }
        });
        responses.addApiResponse(
                DEFAULT_RESPONSE, new ApiResponse().description("Error").content(problemContent()));
    }

    private static Stream<Parameter> parameters(Operation operation) {
        List<Parameter> parameters = operation.getParameters();
        return parameters == null ? Stream.empty() : parameters.stream();
    }

    private static boolean isError(String status) {
        return status.chars().allMatch(Character::isDigit) && Integer.parseInt(status) >= FIRST_ERROR_STATUS;
    }

    private static Content problemContent() {
        return new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(PROBLEM_REF)));
    }

    private static Schema<?> problemSchema() {
        Schema<?> invalidField = new ObjectSchema()
                .description("An invalid field of the request body.")
                .addProperty(
                        "field",
                        new StringSchema()
                                .description("Path of the invalid property in the request body.")
                                .example("feed.maxEpisodes"))
                .addProperty(
                        "message",
                        new StringSchema()
                                .description("Why the value is invalid.")
                                .example("must be greater than 0"));
        return new ObjectSchema()
                .description("RFC 9457 Problem Details, the body of every error response.")
                .addProperty(
                        "type",
                        new StringSchema()
                                .format("uri")
                                .description("URI identifying the problem type; about:blank for plain HTTP errors."))
                .addProperty(
                        "title",
                        new StringSchema()
                                .description("Short summary of the problem type.")
                                .example("Unprocessable Content"))
                .addProperty(
                        "status",
                        new IntegerSchema()
                                .description("HTTP status code.")
                                .example(HttpStatus.UNPROCESSABLE_CONTENT.value()))
                .addProperty(
                        "detail",
                        new StringSchema()
                                .description("Explanation specific to this occurrence of the problem.")
                                .example("Invalid request content."))
                .addProperty(
                        "instance",
                        new StringSchema()
                                .format("uri-reference")
                                .description("URI of the request that caused the problem.")
                                .example("/api/v1/podcasts"))
                .addProperty(
                        "errors",
                        new ArraySchema()
                                .items(invalidField)
                                .description("Invalid fields of the request body (422 only)."));
    }

    /**
     * Describes the members added by Spring HATEOAS, which have no annotations. The Link and Links schemas are left
     * as is: springdoc replaces them after every customizer.
     */
    private static void describeHalMembers(String schemaName, Schema<?> schema) {
        if ("PageMetadata".equals(schemaName)) {
            describeProperties(schema, PAGE_PROPERTY_DESCRIPTIONS);
        }
        var properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        describeIfBlank(properties.get(HAL_LINKS), "Hypermedia links (HAL); self is the URI of the resource.");
        describeIfBlank(properties.get(PAGE), "Pagination metadata.");
        describeEmbedded(properties.get(HAL_EMBEDDED));
    }

    private static void describeEmbedded(@Nullable Schema<?> embedded) {
        if (embedded != null) {
            describeIfBlank(embedded, "Embedded resources (HAL), by relation.");
            if (embedded.getProperties() != null) {
                embedded.getProperties().values().forEach(items -> describeIfBlank(items, "Items of the collection."));
            }
        }
    }

    private static void describeProperties(Schema<?> schema, Map<String, String> descriptions) {
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, property) -> describeIfBlank(property, descriptions.get(name)));
        }
    }

    private static void describeIfBlank(@Nullable Schema<?> schema, @Nullable String description) {
        if (schema != null && !StringUtils.hasText(schema.getDescription())) {
            schema.setDescription(description);
        }
    }
}

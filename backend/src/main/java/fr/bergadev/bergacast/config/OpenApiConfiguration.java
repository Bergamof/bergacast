package fr.bergadev.bergacast.config;

import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description of the API, public in production to help plugging external tools. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    static final String PROBLEM_SCHEMA = "Problem";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM_SCHEMA;
    private static final int FIRST_ERROR_STATUS = 400;
    private static final String DEFAULT_RESPONSE = "default";

    @Bean
    OpenAPI bergacastOpenApi(BergacastProperties properties, BuildProperties buildProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Bergacast API")
                        // Server version; the API version is the /api/v1 prefix.
                        .version(buildProperties.getVersion())
                        .description("Self-hosted podcast publishing: administration API, episode publication by "
                                + "automated tools, public RSS feeds and audio files. Errors are RFC 9457 Problem "
                                + "Details.")
                        .contact(new Contact().name("Bergacast").url("https://github.com/Bergamof/bergacast"))
                        .license(new License().name("AGPL-3.0-only").url("https://www.gnu.org/licenses/agpl-3.0")))
                .servers(List.of(new Server().url(properties.publicBaseUrl().toString())))
                .tags(List.of(
                        new Tag().name("Root").description("Entry point of the API"),
                        new Tag().name("Access tokens").description("Administrator login"),
                        new Tag().name("Podcasts").description("Podcast administration (admin only)"),
                        new Tag().name("Episodes").description("Episode publication and administration"),
                        new Tag()
                                .name("API tokens")
                                .description("Tokens allowing automated tools to publish episodes (admin only)"),
                        new Tag().name("Admin users").description("Administrators of Bergacast (admin only)"),
                        new Tag().name("Public feed").description("Public RSS feeds and audio files")))
                .components(new Components()
                        .addSecuritySchemes(
                                SecuritySchemes.ADMIN_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Short-lived token obtained from POST /api/v1/access-tokens"))
                        .addSecuritySchemes(
                                SecuritySchemes.PODCAST_TOKEN,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("bgc_")
                                        .description("Podcast API token, limited to the episodes of its podcast")));
    }

    /** Documents every error response, and a default one, as Problem Details. */
    @Bean
    OpenApiCustomizer problemDetailsResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            openApi.getPaths().values().stream()
                    .flatMap(pathItem -> pathItem.readOperations().stream())
                    .forEach(operation -> {
                        operation.getResponses().forEach((status, response) -> {
                            if (isError(status)) {
                                response.setContent(problemContent());
                            }
                        });
                        operation
                                .getResponses()
                                .addApiResponse(
                                        DEFAULT_RESPONSE,
                                        new ApiResponse().description("Error").content(problemContent()));
                    });
        };
    }

    private static boolean isError(String status) {
        return status.chars().allMatch(Character::isDigit) && Integer.parseInt(status) >= FIRST_ERROR_STATUS;
    }

    private static Content problemContent() {
        return new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(PROBLEM_REF)));
    }

    private static Schema<?> problemSchema() {
        Schema<?> invalidField =
                new ObjectSchema().addProperty("field", new StringSchema()).addProperty("message", new StringSchema());
        return new ObjectSchema()
                .description("RFC 9457 Problem Details")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty(
                        "errors", new ArraySchema().items(invalidField).description("Validation errors (422 only)"));
    }
}

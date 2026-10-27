package fr.bergadev.bergacast.config;

import fr.bergadev.bergacast.web.EntityTagged;
import fr.bergadev.bergacast.web.SecuritySchemes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/** OpenAPI description of the API, public in production to help plugging external tools. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String API_DESCRIPTION = """
            Self-hosted podcast publishing: automated tools publish episodes (audio file and metadata), Bergacast \
            serves a public RSS feed and public audio URLs for each podcast.

            **Getting started**
            1. Log in as an administrator: `POST /api/v1/access-tokens` returns a short-lived JWT.
            2. Create a podcast: `POST /api/v1/podcasts`.
            3. Create an API token for the publishing tool: `POST /api/v1/podcasts/{slug}/api-tokens`.
            4. Publish episodes with that token: `POST /api/v1/{slug}/episodes`.
            5. Add the feed URL `/{slug}.xml` to a podcast app (feeds ask Apple and Google not to list them).

            **Conventions**: representations are HAL (`_links`, `_embedded`); errors are RFC 9457 Problem Details; \
            updates are JSON Merge Patch (RFC 7396: an absent member is left unchanged, `null` clears it); entities \
            carry an `ETag` usable in `If-None-Match` and `If-Match`; lists are paged with `page`, `size` and \
            `sort` query parameters; dates are ISO 8601.""";

    @Bean
    OpenAPI bergacastOpenApi(BergacastProperties properties, BuildProperties buildProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Bergacast API")
                        // Server version; the API version is the /api/v1 prefix.
                        .version(buildProperties.getVersion())
                        .description(API_DESCRIPTION)
                        .contact(new Contact().name("Bergacast").url("https://github.com/Bergamof/bergacast"))
                        .license(new License().name("AGPL-3.0-only").url("https://www.gnu.org/licenses/agpl-3.0")))
                .servers(List.of(new Server().url(properties.publicBaseUrl().toString())))
                .tags(tags())
                .components(new Components()
                        .addSecuritySchemes(
                                SecuritySchemes.ADMIN_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Administrator JWT obtained from POST /api/v1/access-tokens, "
                                                + "valid for "
                                                + properties.jwt().lifetime().toMinutes()
                                                + " minutes, granting access to the whole API."))
                        .addSecuritySchemes(
                                SecuritySchemes.PODCAST_TOKEN,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("bgc_")
                                        .description("Podcast API token (bgc_…) created by an administrator, "
                                                + "limited to listing, reading and publishing the episodes of its "
                                                + "podcast. Long-lived until revoked.")));
    }

    private static List<Tag> tags() {
        return List.of(
                new Tag()
                        .name("Root")
                        .description("Entry point: server version and links to the top-level resources. " + "Public."),
                new Tag()
                        .name("Access tokens")
                        .description("Administrator login: credentials exchanged for a short-lived JWT. " + "Public."),
                new Tag()
                        .name("Podcasts")
                        .description("Podcasts: channel metadata of the RSS feed, cover image and feed settings. "
                                + "Administrators only."),
                new Tag()
                        .name("Episodes")
                        .description("Episode publication by automated tools (podcast API token or "
                                + "administrator) and episode administration (administrators only)."),
                new Tag()
                        .name("Feeds")
                        .description("RSS feeds written to the object storage, where podcast apps read them. "
                                + "Administrators only."),
                new Tag()
                        .name("API tokens")
                        .description("Long-lived `bgc_…` tokens allowing an automated tool to publish the "
                                + "episodes of one podcast. Administrators only."),
                new Tag().name("Admin users").description("Administrators of Bergacast. Administrators only."),
                new Tag()
                        .name("Public feed")
                        .description("Former URLs of the RSS feeds, audio files and cover images on this server, "
                                + "permanently redirected to the object storage that now serves them. No "
                                + "authentication."));
    }

    /** Documents the conventions shared by all operations (authentication errors, headers, Problem Details...). */
    @Bean
    OpenApiCustomizer apiConventions(@Value("${spring.data.web.pageable.max-page-size}") int maxPageSize) {
        return new OpenApiConventions(maxPageSize);
    }

    /** Documents the ETag header of the successful responses of the operations marked {@link EntityTagged}. */
    @Bean
    OperationCustomizer entityTagHeaders() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.hasMethodAnnotation(EntityTagged.class)) {
                operation.getResponses().forEach((status, response) -> {
                    if (status.startsWith("2") || "304".equals(status)) {
                        response.addHeaderObject(
                                HttpHeaders.ETAG, new Header().$ref(OpenApiConventions.ETAG_HEADER_REF));
                    }
                });
            }
            return operation;
        };
    }
}

package fr.bergadev.bergacast.token;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.server.core.Relation;

/** Representation of an API token. */
@Relation(itemRelation = "apiToken", collectionRelation = "apiTokens")
@Schema(description = "An API token of a podcast. The plain token is only known in the creation response.")
public record ApiTokenResponse(
        @Schema(description = "Identifier of the token.", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID id,

        @Schema(description = "Label identifying the tool using the token.", example = "n8n workflow")
        String name,

        @Schema(description = "First characters of the token, to recognize it.", example = "bgc_Xy3k")
        String tokenPrefix,

        @Schema(description = "Creation time (UTC).", example = "2026-10-22T09:35:00Z")
        Instant createdAt,

        @Schema(
                description =
                        "Time of the last successful authentication with the token (UTC), null if never " + "used.",
                example = "2026-10-25T06:00:12Z",
                nullable = true)
        @Nullable
        Instant lastUsedAt,

        @Schema(
                description = "Plain token, to send as `Authorization: Bearer <token>`. Only present in the "
                        + "creation response: store it safely, it cannot be retrieved later.",
                example = "bgc_Xy3kQ9vN2pL7sT4wR8mZ1bF6hJ0cD5eA3gK9uY2iO7q")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Nullable
        String token) {}

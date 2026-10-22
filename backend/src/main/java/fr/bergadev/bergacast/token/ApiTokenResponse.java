package fr.bergadev.bergacast.token;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.hateoas.server.core.Relation;

/**
 * Representation of an API token.
 *
 * @param id identifier
 * @param name label
 * @param tokenPrefix first characters of the token
 * @param createdAt creation time
 * @param lastUsedAt last successful authentication, {@code null} if never used
 * @param token plain token, only present in the creation response
 */
@Relation(itemRelation = "apiToken", collectionRelation = "apiTokens")
public record ApiTokenResponse(
        UUID id,
        String name,
        String tokenPrefix,
        Instant createdAt,
        @Nullable Instant lastUsedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String token) {}

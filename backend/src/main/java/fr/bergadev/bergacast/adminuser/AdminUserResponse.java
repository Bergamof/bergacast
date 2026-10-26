package fr.bergadev.bergacast.adminuser;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

/** Representation of an administrator. */
@Relation(itemRelation = "adminUser", collectionRelation = "adminUsers")
@Schema(description = "An administrator of Bergacast. The password is never returned.")
public record AdminUserResponse(
        @Schema(description = "Identifier of the administrator.", example = "3f2b8c1d-4e5a-4b6c-8d7e-9f0a1b2c3d4e")
        UUID id,

        @Schema(description = "Login of the administrator.", example = "jane")
        String username,

        @Schema(description = "Creation time (UTC).", example = "2026-10-22T09:00:00Z")
        Instant createdAt) {}

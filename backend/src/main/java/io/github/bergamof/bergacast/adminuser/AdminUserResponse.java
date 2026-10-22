package io.github.bergamof.bergacast.adminuser;

import java.time.Instant;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

/**
 * Representation of an administrator.
 *
 * @param id identifier
 * @param username login
 * @param createdAt creation time
 */
@Relation(itemRelation = "adminUser", collectionRelation = "adminUsers")
public record AdminUserResponse(UUID id, String username, Instant createdAt) {}

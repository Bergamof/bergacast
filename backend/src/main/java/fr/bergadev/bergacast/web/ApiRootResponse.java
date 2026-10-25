package fr.bergadev.bergacast.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Representation of the API root. */
@Schema(description = "Entry point of the API: server version and links to the top-level resources.")
public record ApiRootResponse(
        @Schema(
                description = "Semantic version of the running server (0.0.0-dev outside of a release build). "
                        + "The version of the API itself is the /api/v1 prefix.",
                example = "1.2.0")
        String version) {}

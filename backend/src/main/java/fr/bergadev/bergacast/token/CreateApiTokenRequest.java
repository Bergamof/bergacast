package fr.bergadev.bergacast.token;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of an API token creation. */
@Schema(description = "API token to create.")
public record CreateApiTokenRequest(
        @Schema(description = "Label identifying the tool that will use the token.", example = "n8n workflow")
        @NotBlank
        @Size(max = ApiToken.MAX_NAME_LENGTH)
        String name) {}

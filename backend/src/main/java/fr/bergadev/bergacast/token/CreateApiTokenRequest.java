package fr.bergadev.bergacast.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of an API token creation.
 *
 * @param name label helping to identify the tool using the token
 */
public record CreateApiTokenRequest(
        @NotBlank @Size(max = ApiToken.MAX_NAME_LENGTH) String name) {}

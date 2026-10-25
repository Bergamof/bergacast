package fr.bergadev.bergacast.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Credentials of an administrator. */
@Schema(description = "Credentials of an administrator.")
public record AccessTokenRequest(
        @Schema(description = "Login of the administrator.", example = "admin") @NotBlank
        String username,

        @Schema(description = "Password of the administrator.", example = "admin-password", format = "password")
        @NotBlank
        String password) {

    @Override
    public String toString() {
        return "AccessTokenRequest[username=" + username + ", password=<redacted>]";
    }
}

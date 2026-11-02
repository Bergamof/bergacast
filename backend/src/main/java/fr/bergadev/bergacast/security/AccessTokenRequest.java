package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.adminuser.AdminUser;
import fr.bergadev.bergacast.adminuser.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials of an administrator. Bounded like the stored ones (a valid password has at most
 * {@value PasswordPolicy#MAX_BYTES} bytes, hence characters), so that a login never hashes nor remembers more.
 */
@Schema(description = "Credentials of an administrator.")
public record AccessTokenRequest(
        @Schema(description = "Login of the administrator.", example = "admin")
        @NotBlank
        @Size(max = AdminUser.MAX_USERNAME_LENGTH)
        String username,

        @Schema(description = "Password of the administrator.", example = "admin-password", format = "password")
        @NotBlank
        @Size(max = PasswordPolicy.MAX_BYTES)
        String password) {

    @Override
    public String toString() {
        return "AccessTokenRequest[username=" + username + ", password=<redacted>]";
    }
}

package fr.bergadev.bergacast.adminuser;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of an administrator creation. */
@Schema(description = "Administrator to create.")
public record CreateAdminUserRequest(
        @Schema(description = "Login of the administrator, unique.", example = "jane")
        @NotBlank
        @Size(max = AdminUser.MAX_USERNAME_LENGTH)
        String username,

        @Schema(
                description = "Password, which " + PasswordPolicy.DESCRIPTION + " in UTF-8.",
                example = "correct-horse-battery-staple",
                format = "password")
        @NotNull
        @Password
        String password) {

    @Override
    public String toString() {
        return "CreateAdminUserRequest[username=" + username + ", password=<redacted>]";
    }
}

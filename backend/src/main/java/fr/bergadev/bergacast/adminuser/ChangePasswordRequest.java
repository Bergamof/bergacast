package fr.bergadev.bergacast.adminuser;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of an administrator update: only the password can change. */
@Schema(description = "Change of an administrator: only the password can be modified.")
public record ChangePasswordRequest(
        @Schema(
                description = "New password, which " + PasswordPolicy.DESCRIPTION + " in UTF-8.",
                example = "correct-horse-battery-staple",
                format = "password")
        @NotNull
        @Password
        String password) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[password=<redacted>]";
    }
}

package fr.bergadev.bergacast.adminuser;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of an administrator creation.
 *
 * @param username unique login
 * @param password password, see {@link Password}
 */
public record CreateAdminUserRequest(
        @NotBlank @Size(max = AdminUser.MAX_USERNAME_LENGTH) String username,
        @NotNull @Password String password) {

    @Override
    public String toString() {
        return "CreateAdminUserRequest[username=" + username + ", password=<redacted>]";
    }
}

package fr.bergadev.bergacast.adminuser;

import jakarta.validation.constraints.NotNull;

/**
 * Body of an administrator update: only the password can change.
 *
 * @param password new password, see {@link Password}
 */
public record ChangePasswordRequest(@NotNull @Password String password) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[password=<redacted>]";
    }
}

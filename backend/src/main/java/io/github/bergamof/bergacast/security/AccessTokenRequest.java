package io.github.bergamof.bergacast.security;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials of an administrator.
 *
 * @param username login
 * @param password password
 */
public record AccessTokenRequest(
        @NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "AccessTokenRequest[username=" + username + ", password=<redacted>]";
    }
}

package io.github.bergamof.bergacast.adminuser;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * An administrator as seen by Spring Security during the password login.
 *
 * @param id administrator identifier, used as subject of the issued access tokens
 * @param username login
 * @param passwordHash encoded password
 */
public record AdminPrincipal(UUID id, String username, String passwordHash) implements UserDetails {

    /** The single role of administrators. */
    public static final String ROLE = "ADMIN";

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "AdminPrincipal[id=" + id + ", username=" + username + "]";
    }
}

package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.token.AuthenticatedApiToken;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Authentication by a podcast API token, which only grants access to the episodes of one podcast. */
public final class PodcastTokenAuthentication extends AbstractAuthenticationToken {

    static final String PUBLISHER_AUTHORITY = "ROLE_PODCAST_PUBLISHER";
    private static final long serialVersionUID = 1L;

    private final AuthenticatedApiToken apiToken;

    PodcastTokenAuthentication(AuthenticatedApiToken apiToken) {
        super(List.of(new SimpleGrantedAuthority(PUBLISHER_AUTHORITY)));
        this.apiToken = apiToken;
        setAuthenticated(true);
    }

    public String podcastSlug() {
        return apiToken.podcastSlug();
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedApiToken getPrincipal() {
        return apiToken;
    }

    @Override
    public String getName() {
        return "api-token:" + apiToken.tokenId();
    }
}

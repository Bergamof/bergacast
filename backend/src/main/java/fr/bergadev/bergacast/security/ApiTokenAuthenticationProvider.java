package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.token.ApiTokenService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

/** Authenticates {@code bgc_...} bearer tokens against the stored API token hashes. */
class ApiTokenAuthenticationProvider implements AuthenticationProvider {

    private final ApiTokenService apiTokenService;

    ApiTokenAuthenticationProvider(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String token = ((BearerTokenAuthenticationToken) authentication).getToken();
        return apiTokenService
                .authenticate(token)
                .map(PodcastTokenAuthentication::new)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown or revoked API token"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

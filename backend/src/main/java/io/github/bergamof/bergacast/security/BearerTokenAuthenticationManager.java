package io.github.bergamof.bergacast.security;

import io.github.bergamof.bergacast.token.ApiTokens;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

/** Routes a bearer token to the API token or the admin JWT authentication, depending on its prefix. */
class BearerTokenAuthenticationManager implements AuthenticationManager {

    private final AuthenticationProvider apiTokenProvider;
    private final AuthenticationProvider jwtProvider;

    BearerTokenAuthenticationManager(AuthenticationProvider apiTokenProvider, AuthenticationProvider jwtProvider) {
        this.apiTokenProvider = apiTokenProvider;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String token = ((BearerTokenAuthenticationToken) authentication).getToken();
        return ApiTokens.looksLikeApiToken(token)
                ? apiTokenProvider.authenticate(authentication)
                : jwtProvider.authenticate(authentication);
    }
}

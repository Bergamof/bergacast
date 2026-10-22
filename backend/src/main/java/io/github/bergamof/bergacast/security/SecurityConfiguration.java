package io.github.bergamof.bergacast.security;

import io.github.bergamof.bergacast.adminuser.AdminPrincipal;
import io.github.bergamof.bergacast.config.BergacastProperties;
import io.github.bergamof.bergacast.token.ApiTokenService;
import io.github.bergamof.bergacast.web.ApiPaths;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Stateless security: the API is protected by bearer tokens, everything else (feeds, audio, SPA, OpenAPI) is public.
 *
 * <p>Two kinds of bearer tokens coexist: admin JWTs (HS256, issued by {@code POST /api/v1/access-tokens}) granting
 * {@code ROLE_ADMIN} on the whole API, and podcast API tokens ({@code bgc_...}) limited to listing, reading and
 * publishing the episodes of their podcast.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    private static final String ADMIN_ROLE = "ROLE_" + AdminPrincipal.ROLE;
    private static final String SLUG_VARIABLE = "slug";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            ApiTokenService apiTokenService,
            ProblemDetailsSecurityHandler problemHandler) {
        AuthenticationManager bearerAuthenticationManager = new BearerTokenAuthenticationManager(
                new ApiTokenAuthenticationProvider(apiTokenService), jwtAuthenticationProvider(jwtDecoder));
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, ApiPaths.BASE)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, ApiPaths.ACCESS_TOKENS)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.EPISODES, ApiPaths.EPISODE)
                        .access(adminOrPodcastToken())
                        .requestMatchers(HttpMethod.POST, ApiPaths.EPISODES)
                        .access(adminOrPodcastToken())
                        .requestMatchers(ApiPaths.BASE + "/**")
                        .hasAuthority(ADMIN_ROLE)
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationManagerResolver(request -> bearerAuthenticationManager)
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler));
        return http.build();
    }

    /** Grants admins, and podcast tokens whose podcast is the one of the {@code {slug}} path variable. */
    private static AuthorizationManager<RequestAuthorizationContext> adminOrPodcastToken() {
        return (authenticationSupplier, context) -> {
            var authentication = authenticationSupplier.get();
            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(ADMIN_ROLE::equals);
            boolean isTokenOfPodcast = authentication instanceof PodcastTokenAuthentication podcastToken
                    && podcastToken.podcastSlug().equals(context.getVariables().get(SLUG_VARIABLE));
            return new AuthorizationDecision(isAdmin || isTokenOfPodcast);
        };
    }

    private static JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder jwtDecoder) {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AdminAccessTokenIssuer.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager adminAuthenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(BergacastProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).build();
    }

    @Bean
    JwtDecoder jwtDecoder(BergacastProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    private static SecretKey secretKey(BergacastProperties properties) {
        return new SecretKeySpec(properties.jwt().secretBytes(), HMAC_ALGORITHM);
    }
}

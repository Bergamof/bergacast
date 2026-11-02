package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.adminuser.AdminPrincipal;
import fr.bergadev.bergacast.config.BergacastProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Checks administrator credentials, throttling repeated failures, and issues short-lived HS256 JWTs. */
@Service
class AdminAccessTokenIssuer {

    static final String TOKEN_TYPE = "Bearer";
    static final String ROLES_CLAIM = "roles";
    static final String USERNAME_CLAIM = "preferred_username";

    private final AuthenticationManager adminAuthenticationManager;
    private final LoginThrottle loginThrottle;
    private final JwtEncoder jwtEncoder;
    private final BergacastProperties.Jwt jwtProperties;
    private final Clock clock;

    AdminAccessTokenIssuer(
            AuthenticationManager adminAuthenticationManager,
            LoginThrottle loginThrottle,
            JwtEncoder jwtEncoder,
            BergacastProperties properties,
            Clock clock) {
        this.adminAuthenticationManager = adminAuthenticationManager;
        this.loginThrottle = loginThrottle;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = properties.jwt();
        this.clock = clock;
    }

    /**
     * Issues a token for valid credentials.
     *
     * @throws org.springframework.security.core.AuthenticationException when the credentials are invalid
     * @throws fr.bergadev.bergacast.web.TooManyRequestsException while the username is blocked after failures
     */
    AccessTokenResponse issue(AccessTokenRequest credentials) {
        AdminPrincipal admin = authenticate(credentials);

        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(admin.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.lifetime()))
                .claim(USERNAME_CLAIM, admin.username())
                .claim(ROLES_CLAIM, List.of(AdminPrincipal.ROLE))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessTokenResponse(
                token, TOKEN_TYPE, jwtProperties.lifetime().toSeconds());
    }

    /** Only wrong credentials count as failures: an unavailable database must not block the administrator. */
    private AdminPrincipal authenticate(AccessTokenRequest credentials) {
        String username = credentials.username();
        try (LoginThrottle.LoginAttempt attempt = loginThrottle.begin(username)) {
            Authentication authentication;
            try {
                authentication = adminAuthenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(username, credentials.password()));
            } catch (BadCredentialsException wrongCredentials) {
                attempt.failed();
                throw wrongCredentials;
            }
            attempt.succeeded();
            return (AdminPrincipal) authentication.getPrincipal();
        }
    }
}

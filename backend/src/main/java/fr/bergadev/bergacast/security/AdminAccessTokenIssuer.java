package fr.bergadev.bergacast.security;

import fr.bergadev.bergacast.adminuser.AdminPrincipal;
import fr.bergadev.bergacast.config.BergacastProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Checks administrator credentials and issues short-lived HS256 JWTs. */
@Service
class AdminAccessTokenIssuer {

    static final String TOKEN_TYPE = "Bearer";
    static final String ROLES_CLAIM = "roles";
    static final String USERNAME_CLAIM = "preferred_username";

    private final AuthenticationManager adminAuthenticationManager;
    private final JwtEncoder jwtEncoder;
    private final BergacastProperties.Jwt jwtProperties;
    private final Clock clock;

    AdminAccessTokenIssuer(
            AuthenticationManager adminAuthenticationManager,
            JwtEncoder jwtEncoder,
            BergacastProperties properties,
            Clock clock) {
        this.adminAuthenticationManager = adminAuthenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = properties.jwt();
        this.clock = clock;
    }

    /**
     * Issues a token for valid credentials.
     *
     * @throws org.springframework.security.core.AuthenticationException when the credentials are invalid
     */
    AccessTokenResponse issue(AccessTokenRequest credentials) {
        Authentication authentication = adminAuthenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.username(), credentials.password()));
        AdminPrincipal admin = (AdminPrincipal) authentication.getPrincipal();
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
}

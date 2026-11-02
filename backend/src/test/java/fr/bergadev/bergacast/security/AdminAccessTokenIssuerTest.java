package fr.bergadev.bergacast.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.bergadev.bergacast.config.BergacastProperties;
import fr.bergadev.bergacast.web.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AdminAccessTokenIssuerTest {

    private static final AccessTokenRequest CREDENTIALS = new AccessTokenRequest("admin", "a-wrong-password");

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final LoginThrottle throttle = new LoginThrottle(Clock.systemUTC());
    private final AdminAccessTokenIssuer issuer;

    AdminAccessTokenIssuerTest() {
        BergacastProperties properties = mock(BergacastProperties.class);
        when(properties.jwt())
                .thenReturn(new BergacastProperties.Jwt(
                        "a-secret-of-at-least-thirty-two-bytes", Duration.ofMinutes(15), "test"));
        issuer = new AdminAccessTokenIssuer(
                authenticationManager, throttle, mock(JwtEncoder.class), properties, Clock.systemUTC());
    }

    @Test
    void blocksTheUsernameAfterThreeWrongPasswords() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> issuer.issue(CREDENTIALS)).isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> issuer.issue(CREDENTIALS)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void doesNotCheckThePasswordWhileBlocked() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> issuer.issue(CREDENTIALS)).isInstanceOf(BadCredentialsException.class);
        }
        clearInvocations(authenticationManager);

        assertThatThrownBy(() -> issuer.issue(CREDENTIALS)).isInstanceOf(TooManyRequestsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void doesNotCountAnUnavailableDatabaseAsAFailure() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new InternalAuthenticationServiceException("Database unavailable"));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> issuer.issue(CREDENTIALS))
                    .isInstanceOf(InternalAuthenticationServiceException.class);
        }

        assertThatCode(() -> throttle.begin(CREDENTIALS.username()).close()).doesNotThrowAnyException();
    }
}

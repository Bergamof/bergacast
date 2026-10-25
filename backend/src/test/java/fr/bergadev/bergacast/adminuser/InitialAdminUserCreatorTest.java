package fr.bergadev.bergacast.adminuser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.bergadev.bergacast.config.BergacastProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.util.unit.DataSize;

class InitialAdminUserCreatorTest {

    private final AdminUserService adminUserService = mock(AdminUserService.class);

    @Test
    void createsTheConfiguredAdministratorWhenThereIsNone() {
        run("a-long-enough-password");

        verify(adminUserService).create("admin", "a-long-enough-password");
    }

    @Test
    void failsFastOnAPasswordViolatingThePolicy() {
        assertThatThrownBy(() -> run("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BERGACAST_ADMIN_PASSWORD must contain at least 12 characters");
        assertThatThrownBy(() -> run("é".repeat(40))).isInstanceOf(IllegalStateException.class);
        verify(adminUserService, never()).create(anyString(), anyString());
    }

    @Test
    void doesNothingWhenAnAdministratorExists() {
        when(adminUserService.anyExists()).thenReturn(true);

        run("short");

        verify(adminUserService, never()).create(anyString(), anyString());
    }

    private void run(String password) {
        BergacastProperties properties = new BergacastProperties(
                URI.create("http://localhost"),
                new BergacastProperties.Jwt("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), "x"),
                new BergacastProperties.InitialAdmin("admin", password),
                new BergacastProperties.Cors(List.of()),
                DataSize.ofMegabytes(20));
        new InitialAdminUserCreator(adminUserService, properties).run(new DefaultApplicationArguments());
    }
}

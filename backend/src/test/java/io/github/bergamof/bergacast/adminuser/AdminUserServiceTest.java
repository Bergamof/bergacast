package io.github.bergamof.bergacast.adminuser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.bergamof.bergacast.web.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserServiceTest {

    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final AdminUserService service =
            new AdminUserService(repository, mock(PasswordEncoder.class), Clock.systemUTC());
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void existingAdministrator() {
        when(repository.findById(targetId)).thenReturn(Optional.of(new AdminUser("target", "hash", Instant.EPOCH)));
    }

    @Test
    void refusesToDeleteTheLastAdministrator() {
        when(repository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(targetId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last administrator");
        verify(repository, never()).delete(any());
    }

    @Test
    void refusesToDeleteOneself() {
        when(repository.count()).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(targetId, targetId)).isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void deletesAnotherAdministrator() {
        when(repository.count()).thenReturn(2L);

        service.delete(targetId, UUID.randomUUID());

        verify(repository).delete(any(AdminUser.class));
    }
}

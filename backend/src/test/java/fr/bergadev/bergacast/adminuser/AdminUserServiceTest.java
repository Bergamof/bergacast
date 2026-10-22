package fr.bergadev.bergacast.adminuser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.bergadev.bergacast.web.ConflictException;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminUserServiceTest {

    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final AdminUserService service =
            new AdminUserService(repository, mock(PasswordEncoder.class), Clock.systemUTC());
    private final AdminUser target = administrator();
    private final AdminUser other = administrator();

    @Test
    void refusesToDeleteTheLastAdministrator() {
        when(repository.findAllForUpdate()).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.delete(target.getId(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last administrator");
        verify(repository, never()).delete(any());
    }

    @Test
    void refusesToDeleteOneself() {
        when(repository.findAllForUpdate()).thenReturn(List.of(target, other));

        assertThatThrownBy(() -> service.delete(target.getId(), target.getId())).isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void reportsAnUnknownAdministrator() {
        when(repository.findAllForUpdate()).thenReturn(List.of(target, other));

        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesAnotherAdministrator() {
        when(repository.findAllForUpdate()).thenReturn(List.of(target, other));

        service.delete(target.getId(), other.getId());

        verify(repository).delete(target);
    }

    private static AdminUser administrator() {
        AdminUser adminUser = new AdminUser("user", "hash", Instant.EPOCH);
        ReflectionTestUtils.setField(adminUser, "id", UUID.randomUUID());
        return adminUser;
    }
}

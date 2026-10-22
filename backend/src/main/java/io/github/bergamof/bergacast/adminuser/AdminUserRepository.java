package io.github.bergamof.bergacast.adminuser;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Persistence of {@link AdminUser}s. */
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Loads all administrators and locks them ({@code SELECT ... FOR UPDATE}) until the end of the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdminUser a")
    List<AdminUser> findAllForUpdate();
}

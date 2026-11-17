package fr.bergadev.bergacast.adminuser;

import fr.bergadev.bergacast.web.ConflictException;
import fr.bergadev.bergacast.web.EntityTags;
import fr.bergadev.bergacast.web.ResourceNotFoundException;
import fr.bergadev.bergacast.web.Sha256;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Management of administrators. */
@Service
@Transactional
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    AdminUserService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AdminUser> list(Pageable pageable) {
        return adminUserRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public AdminUser get(UUID id) {
        return adminUserRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No administrator " + id));
    }

    /** @throws ConflictException when the username is already taken */
    public AdminUser create(String username, String password) {
        if (adminUserRepository.existsByUsername(username)) {
            throw new ConflictException("The username '" + username + "' is already taken");
        }
        return adminUserRepository.save(new AdminUser(username, passwordEncoder.encode(password), clock.instant()));
    }

    /** Strong entity tag of an administrator: a digest of the stored password hash, the only modifiable member. */
    public static String entityTag(AdminUser adminUser) {
        return EntityTags.strong(Sha256.hex(adminUser.getPasswordHash().getBytes(StandardCharsets.UTF_8)));
    }

    /** @param ifMatch optional {@code If-Match} header value, checked against the current entity tag */
    public AdminUser changePassword(UUID id, String newPassword, @Nullable String ifMatch) {
        AdminUser adminUser = get(id);
        EntityTags.requireIfMatch(ifMatch, entityTag(adminUser));
        adminUser.changePasswordHash(passwordEncoder.encode(newPassword));
        return adminUser;
    }

    /**
     * Deletes an administrator.
     *
     * @param currentAdminId identifier of the administrator performing the deletion
     * @param ifMatch optional {@code If-Match} header value, checked against the current entity tag
     * @throws ConflictException when deleting oneself or the last administrator, which would lock everybody out
     */
    public void delete(UUID id, UUID currentAdminId, @Nullable String ifMatch) {
        // Locking every administrator serializes concurrent deletions, which could otherwise remove the last two.
        List<AdminUser> administrators = adminUserRepository.findAllForUpdate();
        AdminUser adminUser = administrators.stream()
                .filter(administrator -> administrator.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No administrator " + id));
        EntityTags.requireIfMatch(ifMatch, entityTag(adminUser));
        if (id.equals(currentAdminId)) {
            throw new ConflictException("An administrator cannot delete their own account");
        }
        if (administrators.size() <= 1) {
            throw new ConflictException("The last administrator cannot be deleted");
        }
        adminUserRepository.delete(adminUser);
    }
}

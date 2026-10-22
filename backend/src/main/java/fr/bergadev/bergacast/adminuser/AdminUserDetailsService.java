package fr.bergadev.bergacast.adminuser;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads administrators for the password login. */
@Service
@Transactional(readOnly = true)
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    AdminUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return adminUserRepository
                .findByUsername(username)
                .map(adminUser ->
                        new AdminPrincipal(adminUser.getId(), adminUser.getUsername(), adminUser.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown administrator"));
    }
}

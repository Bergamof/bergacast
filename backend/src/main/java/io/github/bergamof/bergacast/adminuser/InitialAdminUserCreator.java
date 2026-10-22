package io.github.bergamof.bergacast.adminuser;

import io.github.bergamof.bergacast.config.BergacastProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the administrator configured by {@code BERGACAST_ADMIN_USERNAME} / {@code BERGACAST_ADMIN_PASSWORD} when
 * there is none, so that a fresh installation can be administered.
 */
@Component
class InitialAdminUserCreator implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminUserCreator.class);

    private final AdminUserService adminUserService;
    private final BergacastProperties.InitialAdmin initialAdmin;

    InitialAdminUserCreator(AdminUserService adminUserService, BergacastProperties properties) {
        this.adminUserService = adminUserService;
        this.initialAdmin = properties.admin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserService.anyExists()) {
            return;
        }
        if (!initialAdmin.isConfigured()) {
            LOGGER.warn("No administrator exists and BERGACAST_ADMIN_USERNAME / BERGACAST_ADMIN_PASSWORD are not set: "
                    + "nobody can log in to the administration");
            return;
        }
        adminUserService.create(initialAdmin.username(), initialAdmin.password());
        LOGGER.info("Created the initial administrator '{}'", initialAdmin.username());
    }
}

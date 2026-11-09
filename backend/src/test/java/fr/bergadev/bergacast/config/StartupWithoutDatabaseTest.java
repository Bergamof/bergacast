package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.adminuser.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A serverless platform may keep the database paused: restarting the backend must not need it. Here the database does
 * not exist at all, and the production settings (no schema validation, no JDBC metadata) still let the application
 * start. The administrator check made at startup is replaced, since it reads the database by design.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:postgresql://localhost:1/unreachable")
@ActiveProfiles("test")
class StartupWithoutDatabaseTest {

    @MockitoBean
    private AdminUserService adminUserService;

    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithoutReachingTheDatabase() {
        assertThat(context.getStartupDate()).isPositive();
    }
}

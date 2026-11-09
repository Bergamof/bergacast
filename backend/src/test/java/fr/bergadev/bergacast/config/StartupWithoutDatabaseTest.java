package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * A serverless platform may keep the database paused: restarting the backend must not need it. Here the database does
 * not exist at all, and the production settings (no schema validation, no JDBC metadata, no administrator to create:
 * the migration job does it) still let the application start.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:postgresql://localhost:1/unreachable")
@ActiveProfiles("test")
class StartupWithoutDatabaseTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithoutReachingTheDatabase() {
        assertThat(context.getStartupDate()).isPositive();
    }
}

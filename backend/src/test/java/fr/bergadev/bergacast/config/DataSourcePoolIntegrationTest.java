package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import fr.bergadev.bergacast.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class DataSourcePoolIntegrationTest {

    @Autowired
    private HikariDataSource dataSource;

    @Test
    void keepsNoIdleConnectionOpen() {
        assertThat(dataSource.getMinimumIdle()).isZero();
        assertThat(Duration.ofMillis(dataSource.getIdleTimeout())).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void boundsThePoolSizeAndTheConnectionLifetime() {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
        assertThat(Duration.ofMillis(dataSource.getMaxLifetime())).isEqualTo(Duration.ofMinutes(10));
    }
}

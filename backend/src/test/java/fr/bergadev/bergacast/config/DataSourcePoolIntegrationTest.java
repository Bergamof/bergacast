package fr.bergadev.bergacast.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import fr.bergadev.bergacast.support.IntegrationTest;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class DataSourcePoolIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void keepsNoIdleConnectionOpen() throws SQLException {
        assertThat(pool().getMinimumIdle()).isZero();
        assertThat(Duration.ofMillis(pool().getIdleTimeout())).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void boundsThePoolSizeAndTheConnectionLifetime() throws SQLException {
        assertThat(pool().getMaximumPoolSize()).isEqualTo(5);
        assertThat(Duration.ofMillis(pool().getMaxLifetime())).isEqualTo(Duration.ofMinutes(10));
    }

    /** The Hikari pool, behind the data source that tracks connections for the request log. */
    private HikariDataSource pool() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class);
    }
}

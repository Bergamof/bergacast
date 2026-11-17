package fr.bergadev.bergacast.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.zaxxer.hikari.HikariDataSource;
import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The feed is written to the storage once the connection of the change is back in the pool: a serverless database is
 * not kept awake, and the pool is not drained, by network I/O.
 */
@IntegrationTest
class StorageCallsOutsideTransactionIntegrationTest {

    @Autowired
    private ApiTestClient api;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private ObjectStorage storage;

    @Test
    void writesTheFeedWithoutAnyBorrowedConnection() throws Exception {
        AtomicInteger borrowedWhileWriting = new AtomicInteger(-1);
        doAnswer(invocation -> {
                    borrowedWhileWriting.set(pool().getHikariPoolMXBean().getActiveConnections());
                    return invocation.callRealMethod();
                })
                .when(storage)
                .put(anyString(), any(byte[].class), anyString(), any(CacheControl.class));

        api.createPodcast(ApiTestClient.uniqueSlug("pool"));

        assertThat(borrowedWhileWriting).hasValue(0);
    }

    private HikariDataSource pool() throws java.sql.SQLException {
        return dataSource.unwrap(HikariDataSource.class);
    }
}

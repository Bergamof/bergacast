package fr.bergadev.bergacast.support;

import fr.bergadev.bergacast.migration.MigrationMain;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 18 database, migrated by the migration module like the production one, and S3-compatible object storage
 * (Adobe S3Mock), shared by all the application contexts of the test suite.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String BUCKET = "bergacast";
    private static final int S3MOCK_HTTP_PORT = 9090;

    private static final PostgreSQLContainer POSTGRES = migratedPostgres();

    @SuppressWarnings("resource") // stopped by Testcontainers after the run, like the database
    private static final GenericContainer<?> S3MOCK = new GenericContainer<>(
                    DockerImageName.parse("adobe/s3mock:5.2.3"))
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
            .withExposedPorts(S3MOCK_HTTP_PORT)
            .waitingFor(Wait.forHttp("/").forPort(S3MOCK_HTTP_PORT));

    /** The database as the migration job leaves it: migrated, with the administrator the tests log in as. */
    private static PostgreSQLContainer migratedPostgres() {
        PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
        postgres.start();
        int status = MigrationMain.run(Map.of(
                "BERGACAST_DATASOURCE_URL", postgres.getJdbcUrl(),
                "BERGACAST_DATASOURCE_USERNAME", postgres.getUsername(),
                "BERGACAST_DATASOURCE_PASSWORD", postgres.getPassword(),
                "BERGACAST_ADMIN_USERNAME", ApiTestClient.ADMIN_USERNAME,
                "BERGACAST_ADMIN_PASSWORD", ApiTestClient.ADMIN_PASSWORD));
        if (status != MigrationMain.SUCCESS) {
            throw new IllegalStateException("The test database could not be migrated");
        }
        return postgres;
    }

    /** Not stopped with the context: other cached contexts use it too, Testcontainers removes it after the run. */
    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

    /** S3Mock accepts any credentials; the bucket and the keys are set in {@code application-test.yaml}. */
    @Bean
    DynamicPropertyRegistrar objectStorageProperties() {
        return registry -> {
            if (!S3MOCK.isRunning()) {
                S3MOCK.start();
            }
            registry.add(
                    "bergacast.storage.endpoint",
                    () -> "http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(S3MOCK_HTTP_PORT));
        };
    }
}

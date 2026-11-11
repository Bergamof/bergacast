package fr.bergadev.bergacast.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One PostgreSQL 18 container for the whole test run, handing out an empty database per test. */
final class PostgresDatabases {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    private static final AtomicInteger COUNTER = new AtomicInteger();

    static {
        // Stopped by Testcontainers (Ryuk) once the JVM is done.
        POSTGRES.start();
    }

    private PostgresDatabases() {}

    /** JDBC URL of a new empty database. */
    static String createDatabase() throws SQLException {
        String name = "test_" + COUNTER.incrementAndGet();
        try (Connection connection = connectTo(POSTGRES.getDatabaseName());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return urlOf(name);
    }

    static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    static String username() {
        return POSTGRES.getUsername();
    }

    static String password() {
        return POSTGRES.getPassword();
    }

    private static Connection connectTo(String database) throws SQLException {
        return connect(urlOf(database));
    }

    private static String urlOf(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
    }
}

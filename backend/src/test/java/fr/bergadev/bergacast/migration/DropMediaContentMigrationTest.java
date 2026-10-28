package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Migration V4 on a database holding media the way versions before the object storage stored them: their content is
 * freed while their metadata stays. Runs on its own database, since large objects are not scoped to a schema.
 */
@Testcontainers
class DropMediaContentMigrationTest {

    private static final String PODCAST_ID = "11111111-1111-1111-1111-111111111111";
    private static final String AUDIO_FILE_ID = "33333333-3333-3333-3333-333333333333";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    @BeforeAll
    static void migrateADatabaseHoldingMediaContent() throws SQLException {
        flyway("3").migrate();
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO podcast (id, slug, title, description, language, evict_oldest_by, sort_by,
                                         sort_direction, created_at, updated_at, version)
                    VALUES ('%s', 'former', 'Former', 'Media in the database', 'fr', 'PUBLICATION_DATE',
                            'PUBLICATION_DATE', 'DESC', now(), now(), 0)""".formatted(PODCAST_ID));
            statement.execute("""
                    INSERT INTO audio_file (id, content_oid, mime_type, size_bytes, sha256, file_extension)
                    VALUES ('%s', lo_from_bytea(0, '\\x0102'), 'audio/mpeg', 2, 'sha', 'mp3')
                    """.formatted(AUDIO_FILE_ID));
            statement.execute("""
                    INSERT INTO episode (id, podcast_id, audio_file_id, guid, title, publication_date, added_at,
                                         episode_type, version)
                    VALUES ('22222222-2222-2222-2222-222222222222', '%s', '%s', 'former-1', 'Former episode',
                            now(), now(), 'FULL', 0)""".formatted(PODCAST_ID, AUDIO_FILE_ID));
            statement.execute("""
                    INSERT INTO podcast_image (podcast_id, content, mime_type, width, height, size_bytes, sha256)
                    VALUES ('%s', '\\x0304', 'image/png', 1, 1, 2, 'sha')""".formatted(PODCAST_ID));
        }
        flyway(null).migrate();
    }

    @Test
    void unlinksTheLargeObjectsOfTheAudioFiles() throws SQLException {
        assertThat(count("SELECT count(*) FROM pg_largeobject_metadata")).isZero();
    }

    @Test
    void dropsTheContentColumns() throws SQLException {
        assertThat(count("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE (table_name = 'audio_file' AND column_name = 'content_oid')
                           OR (table_name = 'podcast_image' AND column_name = 'content')""")).isZero();
    }

    @Test
    void keepsTheMetadataOfTheMedia() throws SQLException {
        assertThat(count("SELECT count(*) FROM episode")).isOne();
        assertThat(count("SELECT count(*) FROM audio_file WHERE sha256 = 'sha'"))
                .isOne();
        assertThat(count("SELECT count(*) FROM podcast_image WHERE width = 1")).isOne();
    }

    /** Only the trigger that unlinked large objects is gone: deleting an episode still deletes its audio file row. */
    @Test
    void stillDeletesTheAudioFileOfADeletedEpisode() throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("DELETE FROM episode");
            try (ResultSet result = statement.executeQuery("SELECT count(*) FROM audio_file")) {
                result.next();
                assertThat(result.getLong(1)).isZero();
            }
            connection.rollback();
        }
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        return (target == null ? configuration : configuration.target(target)).load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long count(String query) throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getLong(1);
        }
    }
}

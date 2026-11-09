package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The changelog on an empty database, on one the former Flyway migrations already migrated, and run twice. */
class MigratorTest {

    private static final List<String> FLYWAY_MIGRATIONS = List.of(
            "V1__create_schema.sql",
            "V2__create_podcast_image.sql",
            "V3__move_media_to_object_storage.sql",
            "V4__drop_media_content_from_database.sql");

    private static final String PODCAST_ID = "11111111-1111-1111-1111-111111111111";

    /** Tables of the migration tools themselves, which the schema comparison leaves out. */
    private static final String TOOL_TABLES = "('flyway_schema_history', 'databasechangelog', 'databasechangeloglock')";

    /** What the application depends on: columns, constraints, indexes, triggers and their functions. */
    private static final List<String> SCHEMA_QUERIES = List.of(
            """
            SELECT table_name, column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name NOT IN %s
            ORDER BY table_name, column_name""".formatted(TOOL_TABLES),
            """
            SELECT conrelid::regclass, conname, pg_get_constraintdef(oid)
            FROM pg_constraint
            WHERE connamespace = 'public'::regnamespace
              AND conrelid::regclass::text NOT IN %s
            ORDER BY 1, 2""".formatted(TOOL_TABLES),
            """
            SELECT tablename, indexname, indexdef FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename NOT IN %s
            ORDER BY 1, 2""".formatted(TOOL_TABLES),
            "SELECT tgrelid::regclass, pg_get_triggerdef(oid) FROM pg_trigger WHERE NOT tgisinternal ORDER BY 1, 2",
            """
            SELECT proname, prosrc FROM pg_proc WHERE pronamespace = 'public'::regnamespace ORDER BY 1""");

    @Test
    void createsTheSchemaOfAnEmptyDatabase() throws Exception {
        String url = PostgresDatabases.createDatabase();

        migrate(url);

        assertThat(execType(url, "baseline-schema")).isEqualTo("EXECUTED");
        assertThat(tables(url))
                .contains("podcast", "episode", "audio_file", "podcast_image", "api_token", "admin_user");
    }

    @Test
    void createsTheSameSchemaAsTheFlywayMigrations() throws Exception {
        String migratedByLiquibase = PostgresDatabases.createDatabase();
        String migratedByFlyway = PostgresDatabases.createDatabase();

        migrate(migratedByLiquibase);
        applyFlywayMigrations(migratedByFlyway);

        assertThat(schemaOf(migratedByLiquibase)).isEqualTo(schemaOf(migratedByFlyway));
    }

    @Test
    void onlyRecordsTheBaselineOnADatabaseFlywayMigrated() throws Exception {
        String url = PostgresDatabases.createDatabase();
        applyFlywayMigrations(url);
        execute(
                url,
                "INSERT INTO podcast (id, slug, title, description, language, evict_oldest_by, sort_by,"
                        + " sort_direction, created_at, updated_at, version) VALUES ('" + PODCAST_ID
                        + "', 'former', 'Former',"
                        + " 'Already there', 'fr', 'PUBLICATION_DATE', 'PUBLICATION_DATE', 'DESC', now(), now(), 0)");
        List<List<String>> schemaBefore = schemaOf(url);

        migrate(url);

        assertThat(execType(url, "baseline-schema")).isEqualTo("MARK_RAN");
        assertThat(query(url, "SELECT slug FROM podcast")).containsExactly(List.of("former"));
        assertThat(schemaOf(url)).isEqualTo(schemaBefore);
    }

    @Test
    void dropsTheFlywayHistoryTable() throws Exception {
        String url = PostgresDatabases.createDatabase();
        applyFlywayMigrations(url);

        migrate(url);

        assertThat(execType(url, "drop-flyway-history")).isEqualTo("EXECUTED");
        assertThat(tables(url)).doesNotContain("flyway_schema_history");
    }

    @Test
    void doesNothingWhenTheDatabaseIsAlreadyUpToDate() throws Exception {
        String url = PostgresDatabases.createDatabase();
        migrate(url);
        List<List<String>> changesets = query(url, "SELECT id, exectype FROM databasechangelog ORDER BY orderexecuted");

        migrate(url);

        assertThat(query(url, "SELECT id, exectype FROM databasechangelog ORDER BY orderexecuted"))
                .isEqualTo(changesets);
    }

    @Test
    void keepsDeletingTheAudioFileOfADeletedEpisode() throws Exception {
        String url = PostgresDatabases.createDatabase();
        migrate(url);
        execute(url, """
                INSERT INTO podcast (id, slug, title, description, language, evict_oldest_by, sort_by,
                                     sort_direction, created_at, updated_at, version)
                VALUES ('%s', 'p', 'P', 'D', 'fr', 'PUBLICATION_DATE', 'PUBLICATION_DATE', 'DESC', now(), now(), 0);
                INSERT INTO audio_file (id, mime_type, size_bytes, sha256, file_extension)
                VALUES ('33333333-3333-3333-3333-333333333333', 'audio/mpeg', 2, 'sha', 'mp3');
                INSERT INTO episode (id, podcast_id, audio_file_id, guid, title, publication_date, added_at,
                                     episode_type, version)
                VALUES ('22222222-2222-2222-2222-222222222222', '%s', '33333333-3333-3333-3333-333333333333',
                        'g', 'T', now(), now(), 'FULL', 0);
                DELETE FROM episode;""".formatted(PODCAST_ID, PODCAST_ID));

        assertThat(query(url, "SELECT count(*) FROM audio_file")).containsExactly(List.of("0"));
    }

    @Test
    void reportsAMigrationThatCannotRun() throws Exception {
        Connection closed = PostgresDatabases.connect(PostgresDatabases.createDatabase());
        closed.close();

        assertThatThrownBy(() -> new Migrator().migrate(closed)).isInstanceOf(MigrationFailedException.class);
    }

    private static void migrate(String url) throws SQLException {
        new Migrator().migrate(PostgresDatabases.connect(url));
    }

    /** The database as the former backend left it: the Flyway scripts, then the history table Flyway keeps. */
    private static void applyFlywayMigrations(String url) throws IOException, SQLException {
        for (String migration : FLYWAY_MIGRATIONS) {
            try (var script = MigratorTest.class.getResourceAsStream("/legacy-flyway/" + migration)) {
                execute(url, new String(script.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        execute(
                url,
                "CREATE TABLE flyway_schema_history (installed_rank integer PRIMARY KEY, version varchar(50),"
                        + " description varchar(200) NOT NULL, success boolean NOT NULL)");
    }

    private static String execType(String url, String changeSetId) throws SQLException {
        return query(url, "SELECT exectype FROM databasechangelog WHERE id = '" + changeSetId + "'")
                .getFirst()
                .getFirst();
    }

    private static List<String> tables(String url) throws SQLException {
        return query(url, "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'").stream()
                .map(List::getFirst)
                .toList();
    }

    private static List<List<String>> schemaOf(String url) throws SQLException {
        List<List<String>> schema = new ArrayList<>();
        for (String schemaQuery : SCHEMA_QUERIES) {
            schema.addAll(query(url, schemaQuery));
        }
        return schema;
    }

    private static void execute(String url, String sql) throws SQLException {
        try (Connection connection = PostgresDatabases.connect(url);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<List<String>> query(String url, String sql) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = PostgresDatabases.connect(url);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    row.add(result.getString(column));
                }
                rows.add(row);
            }
        }
        return rows;
    }
}

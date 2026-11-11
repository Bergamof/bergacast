package fr.bergadev.bergacast.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String EXISTING_EPISODE_ID = "33333333-3333-3333-3333-333333333333";
    private static final String EXISTING_AUDIO_FILE_ID = "22222222-2222-2222-2222-222222222222";

    /** Tables of the migration tools themselves, which the schema comparison leaves out. */
    private static final String TOOL_TABLES = "('flyway_schema_history', 'databasechangelog', 'databasechangeloglock')";

    /** What the application depends on: columns, constraints, indexes, triggers and their functions. */
    private static final List<String> SCHEMA_QUERIES = List.of(
            """
            SELECT table_name, column_name, data_type, udt_name, character_maximum_length, numeric_precision,
                   numeric_scale, is_nullable, column_default
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
            SELECT proname, pg_get_function_identity_arguments(oid), prosrc,
                   (SELECT lanname FROM pg_language WHERE oid = prolang)
            FROM pg_proc WHERE pronamespace = 'public'::regnamespace ORDER BY 1""",
            """
            SELECT relname, relkind FROM pg_class
            WHERE relnamespace = 'public'::regnamespace
              AND relname NOT LIKE ALL (ARRAY['flyway%%', 'databasechangelog%%'])
            ORDER BY 1""");

    @Test
    void createsTheSchemaOfAnEmptyDatabase() throws Exception {
        String url = PostgresDatabases.createDatabase();

        migrate(url);

        assertThat(query(url, "SELECT id, exectype FROM databasechangelog ORDER BY orderexecuted"))
                .containsExactly(
                        List.of("baseline-schema", "EXECUTED"),
                        List.of("drop-flyway-history", "MARK_RAN"),
                        List.of("add-episode-image", "EXECUTED"));
        assertThat(tables(url))
                .contains("podcast", "episode", "audio_file", "podcast_image", "api_token", "admin_user");
    }

    @Test
    void createsTheSameSchemaOnAnEmptyDatabaseAndOnOneFlywayMigrated() throws Exception {
        String emptyDatabase = PostgresDatabases.createDatabase();
        String flywayMigrated = PostgresDatabases.createDatabase();

        migrate(emptyDatabase);
        applyFlywayMigrations(flywayMigrated);
        migrate(flywayMigrated);

        List<List<String>> schema = schemaOf(emptyDatabase);
        assertThat(schema).hasSizeGreaterThan(50).isEqualTo(schemaOf(flywayMigrated));
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

        migrate(url);

        assertThat(execType(url, "baseline-schema")).isEqualTo("MARK_RAN");
        assertThat(query(url, "SELECT slug FROM podcast")).containsExactly(List.of("former"));
    }

    @Test
    void addsTheOptionalImageOfEpisodesWithoutTouchingExistingEpisodes() throws Exception {
        String url = PostgresDatabases.createDatabase();
        applyFlywayMigrations(url);
        insertEpisode(url, EXISTING_EPISODE_ID, EXISTING_AUDIO_FILE_ID, "NULL, NULL, NULL, NULL, NULL");

        migrate(url);

        assertThat(query(url, "SELECT title, image_mime_type, image_width, image_sha256 FROM episode"))
                .containsExactly(Arrays.asList("Existing", null, null, null));

        assertThat(execType(url, "add-episode-image")).isEqualTo("EXECUTED");
        assertThat(query(
                        url,
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'episode'"
                                + " AND column_name LIKE 'image_%' ORDER BY 1"))
                .containsExactly(
                        List.of("image_height"),
                        List.of("image_mime_type"),
                        List.of("image_sha256"),
                        List.of("image_size_bytes"),
                        List.of("image_width"));
    }

    @Test
    void storesAnEpisodeImageAndRefusesAnImageWithoutPixels() throws Exception {
        String url = PostgresDatabases.createDatabase();
        migrate(url);
        insertPodcast(url);

        insertEpisode(url, EXISTING_EPISODE_ID, EXISTING_AUDIO_FILE_ID, "'image/png', 300, 200, 1234, 'abc'");
        assertThat(query(url, "SELECT image_mime_type, image_width, image_height, image_size_bytes FROM episode"))
                .containsExactly(List.of("image/png", "300", "200", "1234"));
        assertThatThrownBy(() -> insertEpisode(
                        url,
                        "44444444-4444-4444-4444-444444444444",
                        "55555555-5555-5555-5555-555555555555",
                        "'image/png', 0, 200, 1234, 'abc'"))
                .hasMessageContaining("image_width");
    }

    @Test
    void refusesAnEpisodeImageWithMissingMetadata() throws Exception {
        String url = PostgresDatabases.createDatabase();
        migrate(url);
        execute(
                url,
                "INSERT INTO podcast (id, slug, title, description, language, evict_oldest_by, sort_by,"
                        + " sort_direction, created_at, updated_at, version) VALUES ('" + PODCAST_ID
                        + "', 'show', 'Show', 'Desc', 'fr', 'PUBLICATION_DATE', 'PUBLICATION_DATE', 'DESC',"
                        + " now(), now(), 0)");

        assertThatThrownBy(() -> execute(
                        url,
                        "INSERT INTO audio_file (id, mime_type, size_bytes, sha256, file_extension) VALUES"
                                + " ('22222222-2222-2222-2222-222222222222', 'audio/mpeg', 1, 'abc', 'mp3');"
                                + " INSERT INTO episode (id, podcast_id, audio_file_id, guid, title, publication_date,"
                                + " added_at, episode_type, version, image_mime_type) VALUES"
                                + " ('33333333-3333-3333-3333-333333333333', '" + PODCAST_ID
                                + "', '22222222-2222-2222-2222-222222222222', 'g', 'T', now(), now(), 'FULL', 0,"
                                + " 'image/png')"))
                .hasMessageContaining("episode_image_all_or_nothing");
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

    /** The changeset runs in one transaction: nothing of a failed baseline stays, and a retry once fixed succeeds. */
    @Test
    void failsOnAChangesetThatCannotRunThenResumesOnceFixed() throws Exception {
        String url = PostgresDatabases.createDatabase();
        execute(url, "CREATE TABLE admin_user (id integer)");

        assertThatThrownBy(() -> migrate(url)).isInstanceOf(MigrationFailedException.class);
        assertThat(tables(url)).doesNotContain("podcast");

        execute(url, "DROP TABLE admin_user");
        migrate(url);

        assertThat(tables(url)).contains("podcast");
    }

    private static void insertPodcast(String url) throws SQLException {
        execute(
                url,
                "INSERT INTO podcast (id, slug, title, description, language, evict_oldest_by, sort_by,"
                        + " sort_direction, created_at, updated_at, version) VALUES ('" + PODCAST_ID
                        + "', 'show', 'Show', 'Desc', 'fr', 'PUBLICATION_DATE', 'PUBLICATION_DATE', 'DESC',"
                        + " now(), now(), 0)");
    }

    /** Inserts an episode of the podcast {@code PODCAST_ID} (created if missing) and its audio file. */
    private static void insertEpisode(String url, String episodeId, String audioFileId, String imageColumns)
            throws SQLException {
        if (query(url, "SELECT id FROM podcast").isEmpty()) {
            insertPodcast(url);
        }
        execute(
                url,
                "INSERT INTO audio_file (id, mime_type, size_bytes, sha256, file_extension) VALUES ('" + audioFileId
                        + "', 'audio/mpeg', 1, 'abc', 'mp3')");
        boolean hasImageColumns = !query(
                        url,
                        "SELECT 1 FROM information_schema.columns WHERE table_name = 'episode'"
                                + " AND column_name = 'image_mime_type'")
                .isEmpty();
        String imageNames =
                hasImageColumns ? ", image_mime_type, image_width, image_height, image_size_bytes, image_sha256" : "";
        String imageValues = hasImageColumns ? ", " + imageColumns : "";
        execute(
                url,
                "INSERT INTO episode (id, podcast_id, audio_file_id, guid, title, publication_date, added_at,"
                        + " episode_type, version" + imageNames + ") VALUES ('" + episodeId + "', '" + PODCAST_ID
                        + "', '" + audioFileId + "', 'guid-" + episodeId + "', 'Existing', now(), now(), 'FULL', 0"
                        + imageValues + ")");
    }

    private static void migrate(String url) throws SQLException {
        new Migrator().migrate(PostgresDatabases.connect(url));
    }

    /** The database as the former backend left it: the Flyway scripts, then the history table Flyway keeps. */
    private static void applyFlywayMigrations(String url) throws IOException, SQLException {
        for (String migration : FLYWAY_MIGRATIONS) {
            try (var script = MigratorTest.class.getResourceAsStream("/legacy-flyway/" + migration)) {
                execute(url, new String(script.readAllBytes(), StandardCharsets.UTF_8));
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

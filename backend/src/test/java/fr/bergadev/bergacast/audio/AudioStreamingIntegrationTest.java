package fr.bergadev.bergacast.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class AudioStreamingIntegrationTest {

    private static final int AUDIO_SIZE = 2 * AudioDownloads.CHUNK_SIZE + 123;

    @Autowired
    private AudioDownloads audioDownloads;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    /** The Hikari pool, behind the data source that tracks connections for the request log. */
    private HikariDataSource pool;

    private final byte[] audio = new byte[AUDIO_SIZE];
    private String slug;
    private UUID episodeId;

    @BeforeEach
    void publishEpisode() throws Exception {
        pool = dataSource.unwrap(HikariDataSource.class);
        new Random(7).nextBytes(audio);
        slug = ApiTestClient.uniqueSlug("streaming");
        api.createPodcast(slug);
        JsonNode episode = api.publishEpisode(slug, api.adminAuthorization(), "{\"title\":\"Long\"}", audio);
        episodeId = UUID.fromString(episode.get("id").asString());
    }

    @Test
    void writesEachChunkWithoutHoldingATransactionOrAConnection() throws Exception {
        AudioFile audioFile = audioDownloads.find(slug, episodeId);
        ConnectionCheckingOutputStream client = new ConnectionCheckingOutputStream();

        audioDownloads.write(audioFile, 0, AUDIO_SIZE, client);

        assertThat(client.toByteArray()).isEqualTo(audio);
        assertThat(client.writes).isEqualTo(3);
    }

    @Test
    void writesARangeAcrossChunks() throws Exception {
        AudioFile audioFile = audioDownloads.find(slug, episodeId);
        ByteArrayOutputStream client = new ByteArrayOutputStream();
        int offset = AudioDownloads.CHUNK_SIZE - 10;

        audioDownloads.write(audioFile, offset, 20, client);

        assertThat(client.toByteArray()).isEqualTo(java.util.Arrays.copyOfRange(audio, offset, offset + 20));
    }

    @Test
    void reportsContentDeletedBeforeTheDownload() throws Exception {
        AudioFile audioFile = audioDownloads.find(slug, episodeId);
        mockMvc.perform(delete("/api/v1/{slug}/episodes/{id}", slug, episodeId)
                        .header(HttpHeaders.AUTHORIZATION, api.adminAuthorization()))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> audioDownloads.write(audioFile, 0, AUDIO_SIZE, new ByteArrayOutputStream()))
                .isInstanceOf(AudioContentUnavailableException.class);
    }

    /** A client that checks, whenever it receives data, that no database resource is held on its behalf. */
    private final class ConnectionCheckingOutputStream extends ByteArrayOutputStream {

        private int writes;

        @Override
        public void write(byte[] bytes) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            writes++;
            super.write(bytes, 0, bytes.length);
        }
    }
}

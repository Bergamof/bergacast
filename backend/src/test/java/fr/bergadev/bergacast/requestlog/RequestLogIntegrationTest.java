package fr.bergadev.bergacast.requestlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import fr.bergadev.bergacast.support.ApiTestClient;
import fr.bergadev.bergacast.support.IntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RequestLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTestClient api;

    @Autowired
    private DataSource dataSource;

    @Test
    void logsARequestThatDoesNotUseTheDatabase(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1").header(HttpHeaders.USER_AGENT, "test-agent"))
                .andExpect(status().isOk());

        assertThat(output).containsPattern("GET /api/v1 200 \\d+ms db=no ua=\"test-agent\"");
    }

    @Test
    void logsARequestRejectedBySpringSecurity(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/podcasts")).andExpect(status().isUnauthorized());

        assertThat(output).containsPattern("GET /api/v1/podcasts 401 \\d+ms db=no");
    }

    @Test
    void logsARequestThatUsesTheDatabase(CapturedOutput output) throws Exception {
        String slug = ApiTestClient.uniqueSlug("request-log");
        api.createPodcast(slug);

        String admin = api.adminAuthorization();

        mockMvc.perform(get("/api/v1/podcasts/{slug}", slug).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        assertThat(output).containsPattern("GET /api/v1/podcasts/" + slug + " 200 \\d+ms db=yes");
    }

    /** Podcast apps still polling the former feed URL must not wake the serverless database up. */
    @Test
    void logsTheRedirectionOfAFormerFeedUrlWithoutUsingTheDatabase(CapturedOutput output) throws Exception {
        String slug = ApiTestClient.uniqueSlug("request-log-feed");
        api.createPodcast(slug);

        mockMvc.perform(get("/" + slug + ".xml")).andExpect(status().isMovedPermanently());

        assertThat(output).containsPattern("GET /" + slug + "\\.xml 301 \\d+ms db=no");
    }

    @Test
    void keepsTheHikariPoolReachableBehindTheTrackingDataSource() throws Exception {
        assertThat(dataSource).isInstanceOf(ConnectionTrackingDataSource.class);
        assertThat(dataSource.unwrap(HikariDataSource.class)).isNotNull();
    }
}

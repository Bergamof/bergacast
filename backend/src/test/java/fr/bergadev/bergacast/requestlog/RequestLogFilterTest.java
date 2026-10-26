package fr.bergadev.bergacast.requestlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    @Test
    void logsMethodPathStatusAndUserAgentButNotTheQueryString(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sitemap.xml");
        request.setQueryString("secret=value");
        request.addHeader(HttpHeaders.USER_AGENT, "Googlebot/2.1");

        filter.doFilter(
                request, new MockHttpServletResponse(), (req, res) -> ((MockHttpServletResponse) res).setStatus(404));

        assertThat(output).containsPattern("GET /sitemap\\.xml 404 \\d+ms db=no ua=\"Googlebot/2\\.1\"");
        assertThat(output).doesNotContain("secret");
    }

    @Test
    void tellsWhenTheRequestBorrowedADatabaseConnection(CapturedOutput output) throws Exception {
        FilterChain chainUsingTheDatabase = (req, res) -> ConnectionUsage.recordConnectionBorrowed();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/my-podcast.xml"),
                new MockHttpServletResponse(),
                chainUsingTheDatabase);

        assertThat(output).containsPattern("GET /my-podcast\\.xml 200 \\d+ms db=yes ua=\"-\"");
        assertThat(ConnectionUsage.stopTracking())
                .as("tracking ended with the request")
                .isFalse();
    }

    @Test
    void logsAFailedRequestAsAServerError(CapturedOutput output) {
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(
                        new MockHttpServletRequest("POST", "/api/v1/podcasts"),
                        new MockHttpServletResponse(),
                        failingChain))
                .isInstanceOf(ServletException.class);
        assertThat(output).containsPattern("POST /api/v1/podcasts 500 \\d+ms db=no");
    }

    @Test
    void doesNotLogThePlatformProbes(CapturedOutput output) throws Exception {
        for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
            filter.doFilter(new MockHttpServletRequest("GET", probe), new MockHttpServletResponse(), (req, res) -> {});
        }

        assertThat(output).doesNotContain("/actuator/health");
    }

    @Test
    void logsTheAggregateHealthEndpointWhichChecksTheDatabase(CapturedOutput output) throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(),
                (req, res) -> ConnectionUsage.recordConnectionBorrowed());

        assertThat(output).containsPattern("GET /actuator/health 200 \\d+ms db=yes");
    }

    @Test
    void stripsControlCharactersAndQuotesFromTheUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "bot\"\t\u0000/1.0");

        assertThat(RequestLogFilter.userAgent(request)).isEqualTo("bot/1.0");
    }

    @Test
    void truncatesALongUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "a".repeat(RequestLogFilter.MAX_USER_AGENT_LENGTH + 50));

        assertThat(RequestLogFilter.userAgent(request)).hasSize(RequestLogFilter.MAX_USER_AGENT_LENGTH);
    }
}

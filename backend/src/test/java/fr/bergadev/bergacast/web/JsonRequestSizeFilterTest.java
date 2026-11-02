package fr.bergadev.bergacast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletRequest;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JsonRequestSizeFilterTest {

    private static final int LIMIT = Math.toIntExact(JsonRequestSizeFilter.MAX_JSON_BODY.toBytes());

    private final JsonRequestSizeFilter filter = new JsonRequestSizeFilter();

    @Test
    void readsAJsonBodyWithinTheLimit() throws Exception {
        byte[] body = new byte[LIMIT];

        assertThat(readThroughFilter(jsonRequest("/api/v1/podcasts", body, body.length)))
                .hasSize(LIMIT);
    }

    @Test
    void refusesADeclaredLengthAboveTheLimitBeforeReading() {
        MockHttpServletRequest request = jsonRequest("/api/v1/podcasts", new byte[0], LIMIT + 1L);

        assertThatThrownBy(() -> readThroughFilter(request)).isInstanceOf(ContentTooLargeException.class);
    }

    @Test
    void refusesAnUndeclaredLengthAboveTheLimitWhileReading() {
        MockHttpServletRequest request = jsonRequest("/api/v1/podcasts", new byte[LIMIT + 1], -1);

        assertThatThrownBy(() -> readThroughFilter(request)).isInstanceOf(ContentTooLargeException.class);
    }

    @Test
    void boundsJsonMergePatchBodiesToo() {
        MockHttpServletRequest request = jsonRequest("/api/v1/podcasts/demo", new byte[LIMIT + 1], -1);
        request.setContentType("application/merge-patch+json");

        assertThatThrownBy(() -> readThroughFilter(request)).isInstanceOf(ContentTooLargeException.class);
    }

    @Test
    void leavesOtherBodiesAlone() throws Exception {
        MockHttpServletRequest image = jsonRequest("/api/v1/podcasts/demo/image", new byte[LIMIT + 1], -1);
        image.setContentType("image/png");
        MockHttpServletRequest outsideTheApi = jsonRequest("/elsewhere", new byte[LIMIT + 1], -1);

        assertThat(readThroughFilter(image)).hasSize(LIMIT + 1);
        assertThat(readThroughFilter(outsideTheApi)).hasSize(LIMIT + 1);
    }

    private byte[] readThroughFilter(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        ServletRequest filtered = chain.getRequest();
        try (InputStream body = filtered.getInputStream()) {
            return body.readAllBytes();
        }
    }

    private static MockHttpServletRequest jsonRequest(String uri, byte[] body, long declaredLength) {
        MockHttpServletRequest request = new DeclaredLengthRequest(declaredLength);
        request.setMethod("POST");
        request.setRequestURI(uri);
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    /** Lets a test declare any Content-Length, or none (-1, as with chunked transfer). */
    private static final class DeclaredLengthRequest extends MockHttpServletRequest {

        private final long declaredLength;

        DeclaredLengthRequest(long declaredLength) {
            this.declaredLength = declaredLength;
        }

        @Override
        public long getContentLengthLong() {
            return declaredLength;
        }

        @Override
        public int getContentLength() {
            return Math.toIntExact(declaredLength);
        }
    }
}

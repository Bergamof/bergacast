package fr.bergadev.bergacast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;

class LimitedRequestBodyTest {

    private static final DataSize LIMIT = DataSize.ofBytes(10);

    @Test
    void readsABodyWithinTheLimit() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        assertThat(LimitedRequestBody.read(request, LIMIT)).hasSize(10);
    }

    @Test
    void rejectsADeclaredLengthAboveTheLimitBeforeReading() {
        TrackingRequest request = new TrackingRequest(11, new byte[] {1});

        assertThatThrownBy(() -> LimitedRequestBody.read(request, LIMIT))
                .isInstanceOfSatisfying(
                        ContentTooLargeException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE))
                .hasMessage("The request content exceeds the maximum size of 10B");
        assertThat(request.bodyRead).isFalse();
    }

    @Test
    void enforcesTheLimitWhileReadingWhenTheLengthIsUnknown() {
        TrackingRequest request = new TrackingRequest(-1, new byte[11]);

        assertThatThrownBy(() -> LimitedRequestBody.read(request, LIMIT)).isInstanceOf(ContentTooLargeException.class);
    }

    @Test
    void doesNotTrustAnUnderstatedLength() {
        TrackingRequest request = new TrackingRequest(5, new byte[50]);

        assertThatThrownBy(() -> LimitedRequestBody.read(request, LIMIT)).isInstanceOf(ContentTooLargeException.class);
    }

    /** A request whose declared length is independent from its actual body, as on the wire. */
    private static final class TrackingRequest extends MockHttpServletRequest {

        private final long declaredLength;
        private boolean bodyRead;

        TrackingRequest(long declaredLength, byte[] body) {
            this.declaredLength = declaredLength;
            setContent(body);
        }

        @Override
        public long getContentLengthLong() {
            return declaredLength;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            bodyRead = true;
            return super.getInputStream();
        }
    }
}

package fr.bergadev.bergacast.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds the JSON bodies of the API ({@code application/json} and {@code *+json}, such as JSON Merge Patch), which are
 * read in memory: beyond {@link #MAX_JSON_BODY}, reading the body raises a {@link ContentTooLargeException} (413).
 * Audio and image uploads have their own limits (multipart settings, {@link LimitedRequestBody}).
 *
 * <p>The limit applies while the body is read, so that a missing or wrong {@code Content-Length} cannot bypass it, and
 * a declared length above it is refused at the first read.
 */
@Component
public class JsonRequestSizeFilter extends OncePerRequestFilter {

    /** Far above any valid representation (descriptions included), far below what would strain the memory. */
    public static final DataSize MAX_JSON_BODY = DataSize.ofKilobytes(256);

    private static final String API_PREFIX = "/api/";
    private static final String JSON_SUFFIX = "+json";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX) || !isJson(request.getContentType());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new SizeLimitedRequest(request, MAX_JSON_BODY), response);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || mediaType.getSubtype().endsWith(JSON_SUFFIX);
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    private static ContentTooLargeException tooLarge(DataSize limit) {
        return new ContentTooLargeException(
                "The JSON request content exceeds the maximum size of " + limit.toKilobytes() + " KiB");
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private final DataSize limit;
        private @Nullable ServletInputStream limitedStream;
        private @Nullable BufferedReader limitedReader;

        SizeLimitedRequest(HttpServletRequest request, DataSize limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedStream == null) {
                if (getContentLengthLong() > limit.toBytes()) {
                    throw tooLarge(limit);
                }
                limitedStream = new SizeLimitedInputStream(super.getInputStream(), limit);
            }
            return limitedStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (limitedReader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                limitedReader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return limitedReader;
        }
    }

    private static final class SizeLimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final DataSize limit;
        private long bytesRead;

        SizeLimitedInputStream(ServletInputStream delegate, DataSize limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int bytes) {
            bytesRead += bytes;
            if (bytesRead > limit.toBytes()) {
                throw tooLarge(limit);
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}

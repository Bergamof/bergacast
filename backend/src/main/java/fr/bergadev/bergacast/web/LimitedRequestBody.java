package fr.bergadev.bergacast.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.util.unit.DataSize;

/** Reads a raw (non multipart) request body in memory, within a size limit. */
public final class LimitedRequestBody {

    private static final long UNKNOWN_LENGTH = -1;

    private LimitedRequestBody() {}

    /**
     * Returns the whole body of {@code request}.
     *
     * <p>A {@code Content-Length} above the limit is rejected before reading anything; the limit is enforced while
     * reading as well, since the header may be missing (chunked transfer) or wrong.
     *
     * @param limit largest accepted body, below 2 GiB
     * @throws ContentTooLargeException when the body is larger than {@code limit}
     */
    public static byte[] read(HttpServletRequest request, DataSize limit) throws IOException {
        long maxBytes = limit.toBytes();
        long declaredLength = request.getContentLengthLong();
        if (declaredLength != UNKNOWN_LENGTH && declaredLength > maxBytes) {
            throw tooLarge(limit);
        }
        byte[] body = request.getInputStream().readNBytes(Math.toIntExact(maxBytes + 1));
        if (body.length > maxBytes) {
            throw tooLarge(limit);
        }
        return body;
    }

    private static ContentTooLargeException tooLarge(DataSize limit) {
        return new ContentTooLargeException("The request content exceeds the maximum size of " + limit);
    }
}

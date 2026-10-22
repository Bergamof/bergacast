package io.github.bergamof.bergacast.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** The requested byte range lies outside of the representation (HTTP 416). */
public final class RangeNotSatisfiableException extends ApiException {

    public RangeNotSatisfiableException(long representationLength) {
        super(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "The requested range is outside of the content");
        getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + representationLength);
    }
}

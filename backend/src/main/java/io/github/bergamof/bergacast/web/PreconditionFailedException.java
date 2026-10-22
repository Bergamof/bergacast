package io.github.bergamof.bergacast.web;

import org.springframework.http.HttpStatus;

/** A conditional request header ({@code If-Match}) does not match the current representation (HTTP 412). */
public final class PreconditionFailedException extends ApiException {

    public PreconditionFailedException(String detail) {
        super(HttpStatus.PRECONDITION_FAILED, detail);
    }
}

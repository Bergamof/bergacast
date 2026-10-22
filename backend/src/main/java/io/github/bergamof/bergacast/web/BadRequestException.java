package io.github.bergamof.bergacast.web;

import org.springframework.http.HttpStatus;

/** The request cannot be read, e.g. malformed JSON (HTTP 400). */
public final class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }
}

package fr.bergadev.bergacast.web;

import org.springframework.http.HttpStatus;

/** The request conflicts with the current state of the resource (HTTP 409). */
public final class ConflictException extends ApiException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, detail);
    }
}

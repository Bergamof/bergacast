package fr.bergadev.bergacast.web;

import org.springframework.http.HttpStatus;

/** The addressed resource does not exist (HTTP 404). */
public final class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, detail);
    }
}

package fr.bergadev.bergacast.web;

import org.springframework.http.HttpStatus;

/** The request content exceeds the size the API accepts (HTTP 413). */
public final class ContentTooLargeException extends ApiException {

    public ContentTooLargeException(String detail) {
        super(HttpStatus.CONTENT_TOO_LARGE, detail);
    }
}

package io.github.bergamof.bergacast.web;

import org.springframework.http.HttpStatus;

/** The submitted content has a media type the API does not accept (HTTP 415). */
public final class UnsupportedMediaTypeException extends ApiException {

    public UnsupportedMediaTypeException(String detail) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, detail);
    }
}

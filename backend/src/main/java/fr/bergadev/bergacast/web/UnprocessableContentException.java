package fr.bergadev.bergacast.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/** The request is well-formed but its content is semantically invalid (HTTP 422). */
public final class UnprocessableContentException extends ApiException {

    public static final String ERRORS_PROPERTY = "errors";
    public static final String VALIDATION_DETAIL = "The request content is invalid";

    public UnprocessableContentException(List<InvalidField> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, VALIDATION_DETAIL);
        getBody().setProperty(ERRORS_PROPERTY, List.copyOf(errors));
    }

    public UnprocessableContentException(String field, String message) {
        this(List.of(new InvalidField(field, message)));
    }
}

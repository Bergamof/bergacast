package io.github.bergamof.bergacast.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Business error of the HTTP API, rendered as RFC 9457 Problem Details by Spring MVC.
 *
 * <p>The hierarchy is sealed so that every error the API can produce on purpose is listed here.
 */
public abstract sealed class ApiException extends ErrorResponseException
        permits BadRequestException,
                ResourceNotFoundException,
                ConflictException,
                PreconditionFailedException,
                UnprocessableContentException,
                UnsupportedMediaTypeException,
                RangeNotSatisfiableException {

    protected ApiException(HttpStatus status, String detail) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }
}

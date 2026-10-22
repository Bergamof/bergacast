package io.github.bergamof.bergacast.web;

import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * Renders every error raised while handling a request as RFC 9457 Problem Details, without leaking internals.
 *
 * <p>Standard Spring MVC exceptions (404, 405, 406, 413, 415, ...) and {@link ApiException}s are handled by the
 * parent class; this class adds validation details and maps persistence and security exceptions.
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);
    private static final String BODY_FIELD = "$";
    private static final String BEARER_CHALLENGE = "Bearer";
    /** Clients closing the connection (e.g. a player seeking) are routine: logged briefly, without stack trace. */
    private static final DisconnectedClientHelper DISCONNECTED_CLIENTS =
            new DisconnectedClientHelper(ProblemDetailsExceptionHandler.class.getName() + ".disconnected-clients");

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Stream<InvalidField> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new InvalidField(error.getField(), messageOf(error)));
        Stream<InvalidField> globalErrors = exception.getBindingResult().getGlobalErrors().stream()
                .map(error -> new InvalidField(BODY_FIELD, messageOf(error)));
        return unprocessable(Stream.concat(fieldErrors, globalErrors).toList(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<InvalidField> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new InvalidField(
                                fieldOf(error, result.getMethodParameter().getParameterName()), messageOf(error))))
                .toList();
        return unprocessable(errors, request);
    }

    @ExceptionHandler
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        List<InvalidField> errors = exception.getConstraintViolations().stream()
                .map(violation -> new InvalidField(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return unprocessable(errors, request);
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication failed"));
    }

    @ExceptionHandler
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler
    ProblemDetail handleOptimisticLocking(ObjectOptimisticLockingFailureException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.PRECONDITION_FAILED, "The resource has been modified concurrently, fetch it again");
    }

    @ExceptionHandler
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        LOGGER.info(
                "Request rejected by a database constraint: {}",
                exception.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with an existing resource");
    }

    @ExceptionHandler
    ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Unknown sort property '" + exception.getPropertyName() + "'");
    }

    @ExceptionHandler
    @Nullable
    ProblemDetail handleUnexpected(Exception exception) {
        if (DISCONNECTED_CLIENTS.checkAndLogClientDisconnectedException(exception)) {
            return null;
        }
        LOGGER.error("Unexpected error while handling a request", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Object> unprocessable(List<InvalidField> errors, WebRequest request) {
        List<InvalidField> sortedErrors = errors.stream()
                .sorted(Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message))
                .toList();
        UnprocessableContentException exception = new UnprocessableContentException(sortedErrors);
        return handleExceptionInternal(
                exception, exception.getBody(), exception.getHeaders(), exception.getStatusCode(), request);
    }

    private static String fieldOf(MessageSourceResolvable error, @Nullable String parameterName) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }
        return parameterName == null ? BODY_FIELD : parameterName;
    }

    private static String messageOf(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null ? "is invalid" : message;
    }
}

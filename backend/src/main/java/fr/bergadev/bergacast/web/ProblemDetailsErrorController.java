package fr.bergadev.bergacast.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default error page so that errors raised outside of Spring MVC (servlet container, filters)
 * are also rendered as Problem Details, without exception messages nor stack traces.
 */
@Hidden
@RestController
public class ProblemDetailsErrorController implements ErrorController {

    static final String ERROR_PATH = "/error";

    @RequestMapping(path = ERROR_PATH, produces = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = statusOf(request);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatus(status));
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer statusCode) {
            HttpStatus status = HttpStatus.resolve(statusCode);
            if (status != null && status.isError()) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

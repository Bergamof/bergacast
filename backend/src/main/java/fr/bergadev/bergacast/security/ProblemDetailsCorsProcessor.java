package fr.bergadev.bergacast.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

/** Rejects invalid CORS requests with a {@code 403} Problem Details instead of Spring's plain-text body. */
class ProblemDetailsCorsProcessor extends DefaultCorsProcessor {

    private final ProblemDetailsSecurityHandler problemHandler;

    ProblemDetailsCorsProcessor(ProblemDetailsSecurityHandler problemHandler) {
        this.problemHandler = problemHandler;
    }

    @Override
    public boolean processRequest(
            @Nullable CorsConfiguration config, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        boolean accepted = super.processRequest(config, request, response);
        if (!accepted) {
            problemHandler.writeProblem(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "Cross-origin request not allowed for this origin, method or headers");
        }
        return accepted;
    }

    /** The rejection body is written by {@link #processRequest}, which knows the request URI. */
    @Override
    protected void rejectRequest(ServerHttpResponse response) {
        // Nothing written here: the response must stay uncommitted for the Problem Details body.
    }
}

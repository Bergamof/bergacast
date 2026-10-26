package fr.bergadev.bergacast.requestlog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs one line per HTTP request: method, path (without query string), status, duration, whether it used the
 * database and the user agent, e.g. {@code GET /sitemap.xml 404 3ms db=yes ua="Googlebot/2.1"}. Neither headers,
 * bodies nor client addresses are logged. First filter of the chain, so that requests rejected by Spring Security or
 * CORS are logged as well.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLogFilter extends OncePerRequestFilter {

    static final int MAX_USER_AGENT_LENGTH = 200;
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLogFilter.class);
    private static final String NO_USER_AGENT = "-";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        ConnectionUsage.startTracking();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            boolean usedDatabase = ConnectionUsage.stopTracking();
            LOGGER.info(
                    "{} {} {} {}ms db={} ua=\"{}\"",
                    request.getMethod(),
                    request.getRequestURI(),
                    failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                    usedDatabase ? "yes" : "no",
                    userAgent(request));
        }
    }

    /** The user agent, stripped of control characters (no forged log lines) and truncated. */
    static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent == null || userAgent.isBlank()) {
            return NO_USER_AGENT;
        }
        String printable = userAgent
                .codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != '"')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return printable.length() <= MAX_USER_AGENT_LENGTH ? printable : printable.substring(0, MAX_USER_AGENT_LENGTH);
    }
}

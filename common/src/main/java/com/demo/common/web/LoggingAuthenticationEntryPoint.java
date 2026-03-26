package com.demo.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Logs every failed authentication attempt at WARN level before returning 401.
 * Provides an audit trail for detecting brute-force or credential-stuffing attacks.
 * Wire this into each service's {@code SecurityFilterChain} via
 * {@code .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))}.
 */
@Component
public class LoggingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthenticationEntryPoint.class);

    /** Logs the failed attempt and returns HTTP 401 Unauthorized. */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Auth failure: method={} path={} ip={} error={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                authException.getMessage());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
    }
}

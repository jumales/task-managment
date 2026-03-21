package com.demo.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Servlet filter that enriches the MDC with request-scoped context before every controller call.
 * Runs after Spring Security so the JWT principal is already resolved when this filter executes.
 *
 * <p>Fields added to MDC:
 * <ul>
 *   <li>{@code requestId} — unique UUID per HTTP request, useful for correlating logs within one call</li>
 *   <li>{@code method}    — HTTP method (GET, POST, …)</li>
 *   <li>{@code path}      — request URI</li>
 *   <li>{@code userId}    — JWT subject (Keycloak user UUID), absent for unauthenticated requests</li>
 * </ul>
 *
 * <p>All MDC entries are cleared in the {@code finally} block to prevent leaking into the next request
 * on the same thread (important for thread-pool environments).
 */
@Component
public class MdcFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_METHOD      = "method";
    private static final String MDC_PATH        = "path";
    private static final String MDC_USER_ID     = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString());
            MDC.put(MDC_METHOD, request.getMethod());
            MDC.put(MDC_PATH, request.getRequestURI());
            resolveUserId().ifPresent(id -> MDC.put(MDC_USER_ID, id));

            chain.doFilter(request, response);
        } finally {
            // Always clear MDC to avoid leaking values across requests on pooled threads
            MDC.clear();
        }
    }

    /**
     * Extracts the authenticated user's ID from the Spring Security context.
     * Returns empty if the request is unauthenticated or anonymous.
     */
    private Optional<String> resolveUserId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName); // JWT subject = Keycloak user UUID
    }
}

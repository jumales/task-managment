package com.demo.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign interceptor that forwards the Authorization header from the incoming
 * HTTP request to all outgoing Feign calls.
 *
 * <p>Required because downstream services (e.g. user-service) validate JWTs
 * independently. Without this, service-to-service calls return 401.
 * Placed in {@code common} so all services that use Feign share one implementation.
 * Only registered as a bean when Feign is on the classpath.
 *
 * <p>Two token-extraction paths:
 * <ol>
 *   <li><b>Request-scoped path</b> — synchronous Tomcat threads where
 *       {@link RequestContextHolder} is populated. Copies the raw {@code Authorization}
 *       header from the incoming HTTP request.</li>
 *   <li><b>Security-context path</b> — async tasks (e.g. {@code CompletableFuture}
 *       running on a {@code DelegatingSecurityContextExecutor}) where
 *       {@code RequestContextHolder} is null but the Spring {@link SecurityContextHolder}
 *       carries the JWT. Reconstructs the {@code Bearer} header from the token value.</li>
 * </ol>
 */
@Component
@ConditionalOnClass(RequestInterceptor.class)
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Copies the Bearer token into the outgoing Feign request.
     * Tries {@link RequestContextHolder} first (synchronous path); falls back to
     * {@link SecurityContextHolder} for async tasks where the request context is absent.
     */
    @Override
    public void apply(RequestTemplate template) {
        // Primary: synchronous request thread — copy header directly from the HTTP request
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String authHeader = attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
            if (authHeader != null) {
                template.header(AUTHORIZATION_HEADER, authHeader);
                return;
            }
        }
        // Fallback: async thread with propagated SecurityContext (DelegatingSecurityContextExecutor)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            template.header(AUTHORIZATION_HEADER, "Bearer " + jwtAuth.getToken().getTokenValue());
        }
    }
}

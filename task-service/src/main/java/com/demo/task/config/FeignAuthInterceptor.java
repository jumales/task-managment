package com.demo.task.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign interceptor that forwards the Authorization header from the incoming
 * HTTP request to all outgoing Feign calls.
 *
 * <p>Required because downstream services (e.g. user-service) validate JWTs
 * independently. Without this, service-to-service calls return 401.
 */
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Copies the Bearer token from the current request into the Feign request. */
    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;

        String authHeader = attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null) {
            template.header(AUTHORIZATION_HEADER, authHeader);
        }
    }
}

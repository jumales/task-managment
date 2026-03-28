package com.demo.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
 */
@Component
@ConditionalOnClass(RequestInterceptor.class)
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Copies the Bearer token from the current request into the Feign request. */
    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;

        String authHeader = attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null) return;
        template.header(AUTHORIZATION_HEADER, authHeader);
    }
}

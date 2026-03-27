package com.demo.search.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign interceptor that forwards the Authorization header from the incoming
 * HTTP request to all outgoing Feign calls made by search-service.
 */
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

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

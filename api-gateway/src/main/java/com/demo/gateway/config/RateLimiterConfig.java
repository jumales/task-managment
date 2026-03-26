package com.demo.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configures the Redis-backed rate limiter used by the API Gateway.
 * The rate limit key is the JWT subject (user UUID), ensuring each authenticated
 * user has an independent token bucket rather than sharing a global limit.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate-limit key from the authenticated principal name (JWT subject).
     * Falls back to "anonymous" for unauthenticated requests (rejected by security filter
     * before reaching rate limiter, but guard is here for safety).
     */
    @Bean
    public KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> principal.getName())
                .defaultIfEmpty("anonymous");
    }
}

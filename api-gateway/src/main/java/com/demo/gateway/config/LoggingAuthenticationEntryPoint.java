package com.demo.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logs every failed authentication attempt at WARN level and returns 401.
 * Wired into the gateway's reactive security filter chain to provide an audit
 * trail for detecting brute-force or credential-stuffing attacks.
 */
@Component
public class LoggingAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthenticationEntryPoint.class);

    /** Logs the failed attempt and writes HTTP 401 Unauthorized. */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        log.warn("Auth failure: method={} path={} ip={} error={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                exchange.getRequest().getRemoteAddress(),
                ex.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}

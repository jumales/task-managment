package com.demo.e2e.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Replaces JWT security for e2e tests: permits all requests and injects a
 * pre-authenticated ADMIN principal so {@code @PreAuthorize} checks pass.
 * Provides a no-op JwtDecoder so the shared SecurityConfig bean can be
 * constructed without a real Keycloak instance.
 */
@TestConfiguration
public class E2ESecurityConfig {

    /**
     * Overrides the production SecurityFilterChain (order 1 takes priority).
     * Permits all requests and sets ROLE_ADMIN on every call.
     */
    @Bean
    @Order(1)
    SecurityFilterChain e2eFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new AdminAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * No-op JwtDecoder satisfies the SecurityConfig bean dependency without
     * connecting to Keycloak. Never called because the e2e filter chain
     * uses pre-injected ADMIN auth instead of JWT validation.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new org.springframework.security.oauth2.jwt.BadJwtException(
                    "JWT authentication is not supported in e2e tests");
        };
    }

    /** Injects a static ADMIN principal into every request. */
    private static class AdminAuthFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "e2e-admin", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            chain.doFilter(request, response);
        }
    }
}

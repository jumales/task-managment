package com.demo.task;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;
import java.util.UUID;

/**
 * Overrides production security for all integration tests — permits all requests and injects
 * an ADMIN authentication into the security context so that {@code @PreAuthorize} checks pass.
 * Takes precedence via {@code @Order(1)}.
 *
 * <p>Uses a fixed UUID as the principal name so that creator-ID extraction in
 * {@link com.demo.task.controller.TaskController} behaves the same way as in production.
 */
@TestConfiguration
public class TestSecurityConfig {

    /** Fixed UUID used as the authenticated user's ID in all integration tests. */
    public static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Bean
    @Order(1)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                    jakarta.servlet.http.HttpServletResponse response,
                                                    jakarta.servlet.FilterChain chain)
                            throws java.io.IOException, jakarta.servlet.ServletException {
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(TEST_USER_ID.toString(), null,
                                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        );
                        chain.doFilter(request, response);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

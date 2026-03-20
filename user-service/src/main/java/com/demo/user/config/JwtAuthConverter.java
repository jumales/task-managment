package com.demo.user.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a validated JWT into a Spring Security {@link AbstractAuthenticationToken}
 * that carries both realm roles and fine-grained rights as {@link GrantedAuthority} objects.
 *
 * <p>Roles are read from {@code realm_access.roles} (standard Keycloak claim) and mapped
 * to {@code ROLE_<UPPERCASE_ROLE>} — e.g. {@code ROLE_ADMIN}.
 *
 * <p>Rights are read from the custom {@code rights} claim (a string list injected by a
 * Keycloak protocol mapper) and mapped verbatim — e.g. {@code USER_READ}.
 * These match the {@code Right} entity names managed by this service.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * Extracts roles and rights from the JWT and returns an authenticated token.
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                extractRealmRoles(jwt).stream(),
                extractRights(jwt).stream()
        ).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    /** Reads {@code realm_access.roles} and maps each to {@code ROLE_<ROLE>}. */
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return List.of();
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());
    }

    /** Reads the custom {@code rights} claim and maps each value to a {@link GrantedAuthority}. */
    private Collection<GrantedAuthority> extractRights(Jwt jwt) {
        List<String> rights = jwt.getClaimAsStringList("rights");
        if (rights == null) return List.of();
        return rights.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}

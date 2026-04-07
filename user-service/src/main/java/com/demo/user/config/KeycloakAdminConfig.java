package com.demo.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures a {@link WebClient} pre-authorized with a Keycloak service-account token
 * obtained via the OAuth2 client-credentials grant. Used by {@link com.demo.user.keycloak.KeycloakUserClient}
 * to call the Keycloak Admin REST API.
 *
 * <p>Uses {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} (not the default
 * {@code DefaultOAuth2AuthorizedClientManager}) so that tokens can be fetched from
 * non-servlet threads without an active {@code HttpServletRequest}.
 */
@Configuration
public class KeycloakAdminConfig {

    /**
     * Creates an {@link OAuth2AuthorizedClientManager} that supports the client-credentials grant.
     * The service-account variant is required because WebClient filter calls may happen
     * outside of a servlet request context.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        var provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    /**
     * Provides a {@link WebClient} scoped to the Keycloak Admin REST API base URL.
     * Every request is automatically decorated with a Bearer token from the {@code user-service}
     * client-credentials registration.
     */
    @Bean
    public WebClient keycloakAdminClient(
            @Value("${keycloak.admin.base-url}") String baseUrl,
            OAuth2AuthorizedClientManager clientManager) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter((request, next) -> {
                    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                            .withClientRegistrationId("keycloak")
                            .principal("user-service")
                            .build();
                    var authorizedClient = clientManager.authorize(authorizeRequest);
                    ClientRequest authorized = ClientRequest.from(request)
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + authorizedClient.getAccessToken().getTokenValue())
                            .build();
                    return next.exchange(authorized);
                })
                .build();
    }
}

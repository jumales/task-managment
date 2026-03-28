package com.demo.notification.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Configures all Feign clients to attach a Bearer token obtained via the OAuth2
 * client-credentials grant. This allows the notification-service to call other
 * microservices (user-service, task-service) without a user's request context.
 */
@Configuration
public class FeignClientConfig {

    /**
     * RequestInterceptor that fetches (and caches) a service-to-service access token
     * from Keycloak using the client-credentials grant and injects it as an
     * Authorization header on every outgoing Feign request.
     */
    @Bean
    public RequestInterceptor oauth2FeignInterceptor(OAuth2AuthorizedClientManager clientManager) {
        return requestTemplate -> {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId("keycloak")
                    .principal("notification-service")
                    .build();
            var authorizedClient = clientManager.authorize(authorizeRequest);
            if (authorizedClient != null) {
                requestTemplate.header("Authorization",
                        "Bearer " + authorizedClient.getAccessToken().getTokenValue());
            }
        };
    }

    /**
     * Uses {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} instead of the
     * default {@code DefaultOAuth2AuthorizedClientManager} because the notification
     * service fetches tokens from background {@code @Async} threads where no
     * {@code HttpServletRequest} is available.
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
}

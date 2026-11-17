package edu.rutmiit.enterprise.exhibition.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("!session-auth")
@EnableConfigurationProperties(ReviewSettings.class)
public class HttpClientConfig {
    @Bean
    OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        var tokenFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(200)).build());
        tokenFactory.setReadTimeout(Duration.ofMillis(500));
        var tokenClient = new RestClientClientCredentialsTokenResponseClient();
        tokenClient.setRestClient(RestClient.builder().requestFactory(tokenFactory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build());
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(config -> config.accessTokenResponseClient(tokenClient)).build());
        return manager;
    }

    @Bean
    RestClient reviewRestClient(RestClient.Builder builder,
            ReviewSettings settings) {
        var httpClient = HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(settings.readTimeout());
        
        return builder.baseUrl(settings.baseUrl()).requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get("requestId");
                    if (requestId != null) {
                        request.getHeaders().set(RequestIds.HEADER, requestId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
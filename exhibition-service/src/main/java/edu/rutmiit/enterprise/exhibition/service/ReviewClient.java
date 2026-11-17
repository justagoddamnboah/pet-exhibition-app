package edu.rutmiit.enterprise.exhibition.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import edu.rutmiit.enterprise.exhibition.api.ApiException;
import edu.rutmiit.enterprise.exhibition.api.ReviewResponse;

@Service
@Profile("!session-auth")
public class ReviewClient {
    private static final Logger log = LoggerFactory.getLogger(ReviewClient.class);
    private final RestClient reviewRestClient;
    private final ReviewRetry retry;
    private final OAuth2AuthorizedClientManager clients;

    public ReviewClient(RestClient reviewRestClient, ReviewRetry retry,
                        OAuth2AuthorizedClientManager clients) {
        this.reviewRestClient = reviewRestClient;
        this.retry = retry;
        this.clients = clients;
    }

    public ReviewResponse review(UUID petId) {
        log.info("Calling review-service petId={}", petId);
        try {
            var client = clients.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("review")
                    .principal("review-service").build());
            if (client == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Не удалось получить сервисную идентичность");
            }
            return retry.execute(() -> {
                var response = reviewRestClient.get()
                .uri("/internal/reviews/{petId}", petId)
                .headers(headers -> headers.setBearerAuth(client.getAccessToken().getTokenValue()))
                .retrieve()
                .body(ReviewResponse.class);
                if (response == null) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Пустой ответ сервиса отзывов");
                }
                return response;
            });
        } catch (TimeBudget.Exhausted exhausted) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, exhausted.getMessage());
        } catch (RestClientException | OAuth2AuthorizationException failure) {
            String result = HttpFailures.result(failure);
            log.atWarn().addKeyValue("event", "review.unavailable")
                    .addKeyValue("outcome", result).log("Отзывы недоступны");
            throw new ApiException(result.equals("timeout") ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                    "Сервис отзывов недоступен; причина: " + result);
        }
    }
}
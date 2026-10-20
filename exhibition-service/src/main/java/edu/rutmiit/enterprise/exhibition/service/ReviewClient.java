package edu.rutmiit.enterprise.exhibition.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
import static org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver.principal;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import edu.rutmiit.enterprise.exhibition.api.ReviewResponse;

@Service
@Profile("!session-auth")
public class ReviewClient {
    private static final Logger log = LoggerFactory.getLogger(ReviewClient.class);
    private final RestClient reviewRestClient;

    public ReviewClient(RestClient reviewRestClient) {
        this.reviewRestClient = reviewRestClient;
    }

    public ReviewResponse recommend(UUID bookId) {
        log.info("Calling review-service petId={}", bookId);
        return reviewRestClient.get()
                .uri("/internal/reviews/{petId}", bookId)
                .attributes(clientRegistrationId("review"))
                .attributes(principal("exhibition-service"))
                .retrieve()
                .body(ReviewResponse.class);
    }
}
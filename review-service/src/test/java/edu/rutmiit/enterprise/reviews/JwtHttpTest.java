package edu.rutmiit.enterprise.reviews;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtHttpTest {
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static final String ISSUER;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ISSUER = "http://127.0.0.1:" + JWKS.getAddress().getPort();
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            JWKS.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> ISSUER + "/jwks");
    }

    @LocalServerPort int port;
    private static final String REVIEWS = "/internal/reviews/22222222-2222-4222-8222-222222222222";

    @AfterAll static void stopKeys() { JWKS.stop(0); }

    @Test void validTokenAndRoleMatrix() throws Exception {
        String service = token(KEY, ISSUER, "review-service", "service", 300, -5);
        String operator = token(KEY, ISSUER, "review-service", "operator", 300, -5);
        assertThat(call(REVIEWS, service)).isEqualTo(200);
        assertThat(call("/internal/admin/info", service)).isEqualTo(403);
        assertThat(call(REVIEWS, operator)).isEqualTo(403);
        assertThat(call("/internal/admin/info", operator)).isEqualTo(200);
    }

    @Test void missingOrInvalidCredentialsAreRejected() throws Exception {
        assertThat(call(REVIEWS, null)).isEqualTo(401);
        assertThat(call(REVIEWS, "not-a-jwt")).isEqualTo(401);
        assertThat(call(REVIEWS, token(KEY, ISSUER, "other-api", "service", 300, -5))).isEqualTo(401);
        assertThat(call(REVIEWS, token(KEY, ISSUER + "/other", "review-service", "service", 300, -5))).isEqualTo(401);
        assertThat(call(REVIEWS, token(KEY, ISSUER, "review-service", "service", -120, -300))).isEqualTo(401);
        assertThat(call(REVIEWS, token(KEY, ISSUER, "review-service", "service", 600, 300))).isEqualTo(401);
        RSAKey impostor = new RSAKeyGenerator(2048).keyID("test-key").generate();
        assertThat(call(REVIEWS, token(impostor, ISSUER, "review-service", "service", 300, -5))).isEqualTo(401);
    }

    @Test void unsupportedRolesDoNotGrantAccess() throws Exception {
        assertThat(call(REVIEWS, token(KEY, ISSUER, "review-service", "admin", 300, -5))).isEqualTo(403);
    }

    private int call(String path, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (token != null) request.header("Authorization", "Bearer " + token);
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.headers().allValues("set-cookie")).isEmpty();
            return response.statusCode();
        }
    }

    private String token(RSAKey key, String issuer, String audience, String role, long expires, long notBefore) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("test-service")
                .audience(audience).issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(expires)))
                .notBeforeTime(Date.from(now.plusSeconds(notBefore)))
                .claim("realm_access", Map.of("roles", List.of(role))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
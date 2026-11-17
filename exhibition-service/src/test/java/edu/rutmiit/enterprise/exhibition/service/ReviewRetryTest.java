package edu.rutmiit.enterprise.exhibition.service;

import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLHandshakeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import edu.rutmiit.enterprise.exhibition.config.ReviewSettings;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReviewRetryTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    static ReviewSettings settings(Duration wait) {
        return new ReviewSettings("http://localhost:8666", Duration.ofMillis(200), Duration.ofMillis(800),
                Duration.ofMillis(2600), Duration.ofMillis(100), 2, wait, Duration.ofSeconds(1), 0.5);
    }

    ReviewRetry retry(AtomicLong ticker) {
        var settings = settings(Duration.ZERO);
        return new ReviewRetry(settings, registry, ticker::get,
                new RetryDelay(settings, Clock.systemUTC(), () -> 0.5));
    }

    @Test
    void timeoutThenSuccessMakesOneRetryAndTwoAttempts() {
        var calls = new AtomicInteger();
        String result = retry(new AtomicLong()).execute(() -> {
            if (calls.incrementAndGet() == 1) { throw timeout(); }
            return "OK";
        });
        assertThat(result).isEqualTo("OK");
        assertThat(calls).hasValue(2);
        assertThat(registry.get("exhibition.review.retries").counter().count()).isEqualTo(1);
        assertThat(registry.get("exhibition.review.attempts").tag("result", "timeout").counter().count()).isEqualTo(1);
    }

    @Test
    void sustainedTimeoutIsLimitedToTwoAttempts() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> retry(new AtomicLong()).execute(() -> { calls.incrementAndGet(); throw timeout(); }))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(calls).hasValue(2);
        assertThat(registry.get("exhibition.review.operations").tag("result", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("exhibition.review.operations").tag("result", "budget_exhausted").counter().count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400,401,403,404,409,422,500,501,505})
    void permanentHttpStatusesAreNotRetried(int status) {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> retry(new AtomicLong()).execute(() -> {
            calls.incrementAndGet(); throw response(status, null);
        })).isInstanceOf(RestClientResponseException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void certificateAndUnknownHostAreNotBlindlyRetried() {
        assertThat(HttpFailures.retryable(new ResourceAccessException("TLS", new SSLHandshakeException("certificate")))).isFalse();
        assertThat(HttpFailures.retryable(new ResourceAccessException("DNS", new UnknownHostException("bad-host")))).isFalse();
    }

    @Test
    void tooLargeRetryAfterDoesNotSleepOrLaunchAnotherAttempt() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> retry(new AtomicLong()).execute(() -> {
            calls.incrementAndGet(); throw response(429, "30");
        })).isInstanceOf(TimeBudget.Exhausted.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void consumedBudgetPreventsSecondAttempt() {
        var ticker = new AtomicLong();
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> retry(ticker).execute(() -> {
            calls.incrementAndGet(); ticker.addAndGet(Duration.ofMillis(2000).toNanos()); throw timeout();
        })).isInstanceOf(TimeBudget.Exhausted.class);
        assertThat(calls).hasValue(1);
        assertThat(registry.get("exhibition.review.operations").tag("result", "budget_exhausted").counter().count()).isEqualTo(1);
    }

    @Test
    void retryAfterSupportsSecondsDatesPastAndMalformedValues() {
        var settings = settings(Duration.ofMillis(150));
        var delay = new RetryDelay(settings, Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC), () -> 0.5);
        assertThat(delay.millis(1, response(503, "2"))).isEqualTo(2000);
        assertThat(delay.millis(1, response(503, "Thu, 22 Oct 2026 12:00:01 GMT"))).isEqualTo(1000);
        assertThat(delay.millis(1, response(503, "Thu, 22 Oct 2026 11:59:59 GMT"))).isEqualTo(150);
        assertThat(delay.millis(1, response(503, "nonsense"))).isEqualTo(150);
        assertThat(delay.millis(1, response(503, "9999999999999999999999"))).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void boundedExponentialJitterHasExpectedRange() {
        var settings = settings(Duration.ofMillis(150));
        var low = new RetryDelay(settings, Clock.systemUTC(), () -> 0);
        var high = new RetryDelay(settings, Clock.systemUTC(), () -> Math.nextDown(1.0));
        assertThat(low.millis(1, timeout())).isEqualTo(75);
        assertThat(high.millis(1, timeout())).isEqualTo(225);
        assertThat(low.millis(2, timeout())).isEqualTo(150);
        assertThat(high.millis(20, timeout())).isEqualTo(1000);
    }

    @Test
    void admissionCheckUsesMonotonicRemainingTimeAndReserve() {
        var ticker = new AtomicLong();
        var budget = new TimeBudget(Duration.ofMillis(2600), ticker::get);
        assertThat(budget.fits(Duration.ofSeconds(1), Duration.ofMillis(100), 225)).isTrue();
        ticker.set(Duration.ofMillis(1300).toNanos());
        assertThat(budget.fits(Duration.ofSeconds(1), Duration.ofMillis(100), 225)).isFalse();
    }

    private static ResourceAccessException timeout() {
        return new ResourceAccessException("read timeout", new HttpTimeoutException("timed out"));
    }

    private static RestClientResponseException response(int status, String retryAfter) {
        var headers = new HttpHeaders();
        if (retryAfter != null) { headers.set("Retry-After", retryAfter); }
        return new HttpClientErrorException(org.springframework.http.HttpStatusCode.valueOf(status), "failure", headers, null, null);
    }
}
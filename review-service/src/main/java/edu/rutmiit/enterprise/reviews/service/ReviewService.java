package edu.rutmiit.enterprise.reviews.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.rutmiit.enterprise.reviews.api.ReviewController.ReviewResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class ReviewService {
    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private final DiagnosticMode diagnostics;
    private final MeterRegistry registry;
    private final AtomicInteger active = new AtomicInteger();
    private final Map<String, Counter> requests;
    private final Map<String, Timer> durations;

    public ReviewService(DiagnosticMode diagnostics, MeterRegistry registry) {
        this.diagnostics = diagnostics;
        this.registry = registry;
        requests = Map.of(
                "success", registry.counter("review.requests", "result", "success"),
                "failure", registry.counter("review.requests", "result", "failure"));
        durations = Map.of("success", timer("success"), "failure", timer("failure"));
        Gauge.builder("review.active", active, AtomicInteger::get)
                .description("Currently executing review calculations")
                .register(registry);
    }

    private Timer timer(String result) {
        return Timer.builder("review.processing")
                .description("Review calculation time, excluding HTTP and authentication")
                .tag("result", result)
                .serviceLevelObjectives(Duration.ofMillis(500), Duration.ofSeconds(1))
                .publishPercentileHistogram()
                .register(registry);
    }

    public ReviewResponse review(UUID petId) {
        active.incrementAndGet();
        Timer.Sample sample = Timer.start(registry);
        String result = "failure";
        try {
            diagnostics.beforeCall();
            ReviewResponse response = new ReviewResponse(
                    petId, 14, List.of("Trixie is so cute!", "Good girl!"));
            result = "success";
            log.atInfo().addKeyValue("event", "review.completed")
                    .addKeyValue("outcome", result).log("Review completed");
            return response;
        } catch (RuntimeException failure) {
            log.atWarn().addKeyValue("event", "review.failed")
                    .addKeyValue("outcome", result)
                    .addKeyValue("failureType", failure.getClass().getSimpleName())
                    .log("Review failed");
            throw failure;
        } finally {
            sample.stop(durations.get(result));
            requests.get(result).increment();
            active.decrementAndGet();
        }
    }
}
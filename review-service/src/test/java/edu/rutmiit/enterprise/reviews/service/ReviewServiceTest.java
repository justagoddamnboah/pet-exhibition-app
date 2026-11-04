package edu.rutmiit.enterprise.reviews.service;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReviewServiceTest {
    @Test
    void completedAndFailedCallsHaveBoundedTagsAndReleaseActiveGauge() {
        var registry = new SimpleMeterRegistry();
        try {
            var mode = new DiagnosticMode();
            var service = new ReviewService(mode, registry);
            for (int i = 0; i < 5; i++) {
                service.recommend(UUID.randomUUID());
            }
            mode.change(DiagnosticMode.Mode.FAIL, 30);
            assertThatThrownBy(() -> service.recommend(UUID.randomUUID()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThat(registry.get("review.requests").tag("result", "success").counter().count())
                    .isEqualTo(5);
            assertThat(registry.get("review.requests").tag("result", "failure").counter().count())
                    .isEqualTo(1);
            assertThat(registry.get("review.processing").tag("result", "failure").timer().count())
                    .isEqualTo(1);
            assertThat(registry.get("review.active").gauge().value()).isZero();
            assertThat(registry.getMeters()).allSatisfy(meter ->
                    assertThat(meter.getId().getTags()).allSatisfy(tag -> {
                        assertThat(tag.getKey()).isIn("result", "le");
                        if (tag.getKey().equals("result")) {
                            assertThat(tag.getValue()).isIn("success", "failure");
                        } else {
                            assertThat(tag.getValue()).isIn("0.5", "1");
                        }
                    }));
        } finally {
            registry.close();
        }
    }
}
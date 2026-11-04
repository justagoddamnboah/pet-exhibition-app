package edu.rutmiit.enterprise.reviews.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import edu.rutmiit.enterprise.reviews.service.DiagnosticMode;
import edu.rutmiit.enterprise.reviews.service.ReviewService;

@RestController
@RequestMapping("/internal")
public class ReviewController {
    private final ReviewService reviews;
    private final DiagnosticMode diagnostics;

    public ReviewController(ReviewService reviews, DiagnosticMode diagnostics) {
        this.reviews = reviews;
        this.diagnostics = diagnostics;
    }

    @GetMapping("/reviews/{petId}")
    @PreAuthorize("hasRole('SERVICE')")
    public ReviewResponse reviews(@PathVariable UUID petId) {
        return reviews.recommend(petId);
    }

    @GetMapping("/admin/info")
    @PreAuthorize("hasRole('OPERATOR')")
    public Map<String, String> info() {
        return Map.of("service", "review-service", "status", "ok");
    }

    @PutMapping("/admin/failure-mode")
    @PreAuthorize("hasRole('OPERATOR')")
    public DiagnosticMode.Setting changeMode(@RequestParam DiagnosticMode.Mode mode,
                                            @RequestParam(defaultValue = "90") int seconds) {
        return diagnostics.change(mode, seconds);
    }

    @GetMapping("/admin/failure-mode")
    @PreAuthorize("hasRole('OPERATOR')")
    public DiagnosticMode.Setting currentMode() {
        return diagnostics.current();
    }

    public record ReviewResponse(UUID petId, int likesCount, List<String> comments) {}
}
package edu.rutmiit.enterprise.reviews.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class ReviewController {
    @GetMapping("/reviews/{petId}")
    @PreAuthorize("hasRole('SERVICE')")
    public ReviewResponse reviews(@PathVariable UUID petId) {
        return new ReviewResponse(petId, 14, List.of("Trixie is so cute!", "Good boy!"));
    }

    @GetMapping("/admin/info")
    @PreAuthorize("hasRole('OPERATOR')")
    public Map<String, String> info() {
        return Map.of("service", "review-service", "status", "ok");
    }

    public record ReviewResponse(UUID petId, int likesCount, List<String> comments) {}
}
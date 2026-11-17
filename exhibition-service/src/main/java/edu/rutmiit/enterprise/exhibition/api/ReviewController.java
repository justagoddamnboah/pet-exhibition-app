package edu.rutmiit.enterprise.exhibition.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.rutmiit.enterprise.exhibition.service.PetService;
import edu.rutmiit.enterprise.exhibition.service.ReviewClient;

@RestController
@Profile("!session-auth")
@RequestMapping("/api/pets")
public class ReviewController {
    private final PetService pets;
    private final ReviewClient reviews;

    public ReviewController(PetService pets, ReviewClient reviews) {
        this.pets = pets;
        this.reviews = reviews;
    }

    @GetMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('VISITOR','ADMIN')")
    public ReviewResponse Review(@PathVariable UUID id) {
        pets.pet(id);
        return reviews.review(id);
    }
}
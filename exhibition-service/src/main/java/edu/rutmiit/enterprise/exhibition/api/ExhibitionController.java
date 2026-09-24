package edu.rutmiit.enterprise.exhibition.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.rutmiit.enterprise.exhibition.service.PetService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ExhibitionController {
    private final PetService petService;

    public ExhibitionController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/owners")
    @PreAuthorize("hasAnyRole('ADMIN','VISITOR')")
    List<OwnerResponse> owners() {
        return petService.owners();
    }

    @PostMapping("/owners")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    OwnerResponse createOwner(@Valid @RequestBody CreateOwnerRequest request) {
        return petService.createOwner(request);
    }

    @GetMapping("/pets")
    @PreAuthorize("hasAnyRole('ADMIN','VISITOR')")
    List<PetResponse> pets() {
        return petService.pets();
    }

    @GetMapping("/pets/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VISITOR')")
    PetResponse pet(@PathVariable UUID id) {
        return petService.pet(id);
    }

    @PostMapping("/pets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PetResponse createPet(@Valid @RequestBody CreatePetRequest request) {
        return petService.createPet(request);
    }
}
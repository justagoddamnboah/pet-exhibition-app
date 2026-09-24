package edu.rutmiit.enterprise.exhibition.api;

import java.util.UUID;

import edu.rutmiit.enterprise.exhibition.domain.Sex;
import edu.rutmiit.enterprise.exhibition.domain.Species;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePetRequest(
        @NotBlank @Size(max = 100) String petName,
        @Min(0) @Max(600) Integer ageMonths,
        @NotNull Sex sex,
        @NotNull @Size(max = 50) Species species,
        @NotNull @Size(max = 100) String breed,
        @NotNull UUID ownerId
) {
        
}
package edu.rutmiit.enterprise.exhibition.api;

import java.util.UUID;

import edu.rutmiit.enterprise.exhibition.domain.Sex;
import edu.rutmiit.enterprise.exhibition.domain.Species;

public record PetResponse(
        UUID id,
        String petName,
        Integer ageMonths,
        Sex sex,
        Species species,
        String breed,
        UUID ownerId,
        String ownerName) {

}
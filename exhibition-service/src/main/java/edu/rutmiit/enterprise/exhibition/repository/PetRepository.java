package edu.rutmiit.enterprise.exhibition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.rutmiit.enterprise.exhibition.domain.PetEntity;
import edu.rutmiit.enterprise.exhibition.domain.Species;

public interface PetRepository extends JpaRepository<PetEntity, UUID> {
    boolean existsByPetName(String petName);

    boolean existsBySpecies(Species species);

    boolean existsByOwnerId(UUID id);

    @Override
    List<PetEntity> findAll();

    @Override
    Optional<PetEntity> findById(UUID id);
}
package edu.rutmiit.enterprise.exhibition.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.rutmiit.enterprise.exhibition.api.ApiException;
import edu.rutmiit.enterprise.exhibition.api.CreateOwnerRequest;
import edu.rutmiit.enterprise.exhibition.api.CreatePetRequest;
import edu.rutmiit.enterprise.exhibition.api.OwnerResponse;
import edu.rutmiit.enterprise.exhibition.api.PetResponse;
import edu.rutmiit.enterprise.exhibition.domain.OwnerEntity;
import edu.rutmiit.enterprise.exhibition.domain.PetEntity;
import edu.rutmiit.enterprise.exhibition.repository.OwnerRepository;
import edu.rutmiit.enterprise.exhibition.repository.PetRepository;

@Service
public class PetService {
    private final PetRepository petRepository;
    private final OwnerRepository ownerRepository;

    public PetService(PetRepository petRepository, OwnerRepository ownerRepository) {
        this.petRepository = petRepository;
        this.ownerRepository = ownerRepository;
    }

    @Transactional
    public OwnerResponse createOwner(CreateOwnerRequest request) {
        OwnerEntity owner = ownerRepository.save(
                new OwnerEntity(UUID.randomUUID(), request.name().trim(), request.age())
        );
        return new OwnerResponse(owner.getId(), owner.getName(), owner.getAge());
    }

    @Transactional(readOnly = true)
    public List<OwnerResponse> owners() {
        return ownerRepository.findAll().stream()
                .map(owner -> new OwnerResponse(owner.getId(), owner.getName(), owner.getAge()))
                .toList();
    }

    @Transactional
    public PetResponse createPet(CreatePetRequest request) {
        if (petRepository.existsByPetName(request.petName()) && petRepository.existsByOwnerId(request.ownerId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Питомец с таким хозяином и кличкой уже добавлен");
        }
        OwnerEntity owner = ownerRepository.findById(request.ownerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Хозяин не найден"));
        PetEntity pet = petRepository.save(new PetEntity(
                UUID.randomUUID(),
                request.petName().trim(),
                request.ageMonths(),
                request.sex(),
                request.species(),
                owner
        ));
        return toResponse(pet);
    }

    @Transactional(readOnly = true)
    public List<PetResponse> pets() {
        return petRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PetResponse pet(UUID id) {
        PetEntity pet = petRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Питомец не найден"));
        return toResponse(pet);
    }

    private PetResponse toResponse(PetEntity pet) {
        return new PetResponse(
                pet.getId(),
                pet.getPetName(),
                pet.getAgeMonths(),
                pet.getSex(),
                pet.getSpecies(),
                pet.getOwner().getId(),
                pet.getOwner().getName()
        );
    }
}
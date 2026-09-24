package edu.rutmiit.enterprise.exhibition.repository;

import edu.rutmiit.enterprise.exhibition.domain.OwnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OwnerRepository extends JpaRepository<OwnerEntity, UUID> {

}
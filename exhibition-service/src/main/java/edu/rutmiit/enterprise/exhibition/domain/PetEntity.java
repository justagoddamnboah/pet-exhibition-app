package edu.rutmiit.enterprise.exhibition.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pets")
public class PetEntity {
    @Id
    private UUID id;

    @Column(name = "pet_name", unique = true, nullable = false, length = 100)
    private String petName;

    @Column(name = "age_months", nullable = false)
    private Integer ageMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Species species;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private OwnerEntity owner;

    @Version
    private long version;

    protected PetEntity() {

    }

    public PetEntity(UUID id, String petName, Integer ageMonths, Sex sex, Species species, OwnerEntity owner) {
        this.id = id;
        this.petName = petName;
        this.ageMonths = ageMonths;
        this.sex = sex;
        this.species = species;
        this.owner = owner;
    }

    public UUID getId() {
        return id;
    }

    public String getPetName() {
        return petName;
    }

    public Integer getAgeMonths() {
        return ageMonths;
    }

    public Sex getSex() {
        return sex;
    }

    public Species getSpecies() {
        return species;
    }

    public OwnerEntity getOwner() {
        return owner;
    }

    public long getVersion() {
        return version;
    }
}
package edu.rutmiit.enterprise.exhibition.api;

import java.util.UUID;

public record OwnerResponse(UUID id, String name, Integer age) {
    
}
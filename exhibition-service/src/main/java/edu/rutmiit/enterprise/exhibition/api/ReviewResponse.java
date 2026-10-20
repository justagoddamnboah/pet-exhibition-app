package edu.rutmiit.enterprise.exhibition.api;

import java.util.List;
import java.util.UUID;

public record ReviewResponse(UUID petId, int likesCount, List<String> comments) {}
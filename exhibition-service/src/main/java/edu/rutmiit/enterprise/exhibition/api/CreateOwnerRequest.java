package edu.rutmiit.enterprise.exhibition.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateOwnerRequest(
        @NotBlank @Size(max = 200) String name, @NotNull @Min(0) @Max(99) Integer age
) {
        
}
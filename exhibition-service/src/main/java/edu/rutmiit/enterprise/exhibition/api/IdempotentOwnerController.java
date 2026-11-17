package edu.rutmiit.enterprise.exhibition.api;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import edu.rutmiit.enterprise.exhibition.service.IdempotentOwnerService;
import jakarta.validation.Valid;


@RestController
public class IdempotentOwnerController {
    private final IdempotentOwnerService owners;

    public IdempotentOwnerController(IdempotentOwnerService owners) {
        this.owners = owners;
    }

    @PostMapping("/api/owners/idempotent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OwnerResponse> create(Principal principal,
            @RequestHeader("Idempotency-Key") String rawKey, @Valid @RequestBody CreateOwnerRequest request) {
        if (!rawKey.matches("(?i)^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key должен быть полным UUID без кавычек");
        }
        var result = owners.create(principal.getName(), UUID.fromString(rawKey), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.owner());
    }
}
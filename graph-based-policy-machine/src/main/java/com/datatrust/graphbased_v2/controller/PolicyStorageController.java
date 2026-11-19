package com.datatrust.graphbased_v2.controller;

import com.datatrust.graphbased_v2.controller.dto.PolicyStorageDTO;
import com.datatrust.graphbased_v2.repository.PolicyStorageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/application/{applicationId}/access")
public class PolicyStorageController {
    private final PolicyStorageRepository storage;

    public PolicyStorageController(PolicyStorageRepository storage) { this.storage = storage; }

    @GetMapping("/{objectId}/policy-storage")
    public ResponseEntity<PolicyStorageDTO> get(@PathVariable UUID applicationId, @PathVariable UUID objectId,
                                              @RequestParam UUID identityId, @RequestParam UUID requestedById) {
        return storage.read(applicationId, objectId, identityId, requestedById)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}

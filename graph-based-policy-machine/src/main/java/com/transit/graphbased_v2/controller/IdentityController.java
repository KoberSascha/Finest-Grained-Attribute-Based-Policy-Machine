package com.transit.graphbased_v2.controller;

import com.transit.graphbased_v2.controller.dto.IdentityDTO;
import com.transit.graphbased_v2.controller.dto.IdentityResponseDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.domain.graph.nodes.IdentityClazz;
import com.transit.graphbased_v2.exceptions.NodeIdExistsException;
import com.transit.graphbased_v2.exceptions.NodeNotFoundException;
import com.transit.graphbased_v2.service.IdentityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/identity")
@Tag(name = "Helpers", description = "Operations related to identities")
public class IdentityController {


    @Autowired
    private IdentityService identityService;

    @NotNull
    private static IdentityResponseDTO createResponse(UUID id, String name) {
        var responsevalue = new IdentityResponseDTO(id, name);
        return responsevalue;
    }

    @PostMapping
    public ResponseEntity createIdentityNode(@RequestBody IdentityDTO identityDTO) throws NodeIdExistsException {

        IdentityClazz nodeDTO = new IdentityClazz();
        nodeDTO.setId(identityDTO.getIdentityId());
        nodeDTO.setName("identity#" + identityDTO.getIdentityId());
        nodeDTO.setType(ClazzType.I);
        nodeDTO.setEntityClass(null);

        IdentityClazz createdNodeDTO = identityService.createIdentity(nodeDTO);

        if (createdNodeDTO == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final var responseDTO = createResponse(createdNodeDTO.getId(), createdNodeDTO.getName());

        return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
    }

    @GetMapping("/{identityId}")
    public ResponseEntity getIdentityNode(@PathVariable("identityId") UUID identityId) {

        Optional<IdentityClazz> nodeDTO = identityService.getIdentity(identityId);

        if (!nodeDTO.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final var responseDTO = createResponse(nodeDTO.get().getId(), nodeDTO.get().getName());

        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }


    @DeleteMapping("/{identityId}")
    public ResponseEntity deleteIdentityNode(@PathVariable("identityId") UUID identityId) throws NodeNotFoundException {
        identityService.deleteIdentity(identityId);
        return new ResponseEntity(HttpStatus.NO_CONTENT);
    }


}

package com.transit.graphbased_v2.controller;

import com.transit.graphbased_v2.controller.dto.AccessResponseDTO;
import com.transit.graphbased_v2.controller.dto.EntityPropertiesDTO;
import com.transit.graphbased_v2.controller.dto.OAPropertiesDTO;
import com.transit.graphbased_v2.controller.dto.ObjectDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.domain.graph.nodes.IdentityClazz;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.repository.AssigmentRepository;
import com.transit.graphbased_v2.repository.HelpersRepository;
import com.transit.graphbased_v2.repository.ObjectClazzRepository;
import com.transit.graphbased_v2.service.*;
import com.transit.graphbased_v2.transferobjects.AccessTransferComponent;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/application/{applicationId}/helpers")
@Tag(name = "Helpers", description = "Needful operations")
public class HelpersController {
    @Autowired
    private ObjectClazzRepository objectClazzRepository;

    @Autowired
    private HelpersRepository helpersRepository;

    @Autowired
    private AssigmentRepository assigmentRepository;

    @Autowired
    private HelperService helperService;


    @Autowired
    private AccessService accessService;


    @Autowired
    private ObjectService objectService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private IdentityService identityService;

    @NotNull
    private static AccessResponseDTO createResponse(UUID id, UUID identityId, AccessTransferComponent result) {
        OAPropertiesDTO props = new OAPropertiesDTO(result.getReadProperties(), result.getWriteProperties(), result.getShareReadProperties(), result.getShareWriteProperties(), null);
        var responsevalue = new AccessResponseDTO(id, result.getObjectEntityClazz(), identityId, props);
        return responsevalue;
    }

 /*   @GetMapping("/isshared/{id}")
    public ResponseEntity getIsShared(@PathVariable("applicationId") UUID applicationId, @PathVariable("id") UUID id) {
        Boolean response = false;

        applicationService.verifyApplicationExists(applicationId);
        var node = helpersRepository.findApplicationObjectById(applicationId, id);
        if (node.isPresent()) {
            var outgoingEdges = assigmentRepository.getOutgoingRelationships(id);
            if (outgoingEdges.size() > 1) {
                response = true;
            }
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }*/

    @GetMapping("/entityclass/{objectId}")
    public ResponseEntity getEntityClass(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID id) {

        applicationService.verifyApplicationExists(applicationId);
        String response = null;


        var node = objectClazzRepository.findById(id);


        if (node.isPresent()) {
            response = node.get().getEntityClass();
        } else {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/entity/renameProperty")
    public ResponseEntity<Object> renameEntityProperty(@PathVariable("applicationId") UUID applicationId, @RequestParam(value = "requestedById") UUID requestedById, @RequestBody EntityPropertiesDTO requestDTO) throws BadRequestException {
        applicationService.verifyApplicationExists(applicationId);
        var x = helperService.renamePropertyOfEntity(applicationId, requestedById, requestDTO);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Entityproperty '" + requestDTO.getPropertyOldName() + "' renamed to '" + requestDTO.getPropertyNewName() + "' for all objects of entity '" + requestDTO.getEntityClass() + "'.");

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    @PostMapping("/entity/addProperty")
    public ResponseEntity<Object> addEntityProperty(@PathVariable("applicationId") UUID applicationId, @RequestParam(value = "requestedById") UUID requestedById, @RequestBody EntityPropertiesDTO requestDTO) throws BadRequestException {
        applicationService.verifyApplicationExists(applicationId);
        var x = helperService.addPropertyOfEntity(applicationId, requestedById, requestDTO);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Entityproperty '" + requestDTO.getPropertyNewName() + "' added to all objects of entity '" + requestDTO.getEntityClass() + "'.");

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    @PostMapping("/object/access")
    public ResponseEntity<Object> createIdentitiesAddObjectAndGiveAccess(@PathVariable("applicationId") UUID applicationId, @RequestParam(value = "identityId") UUID identityId, @RequestParam(value = "requestedById") UUID requestedById, @RequestBody ObjectDTO requestDTO) throws BadRequestException {

        applicationService.verifyApplicationExists(applicationId);
        var opt = identityService.getIdentity(identityId);

        if (opt.isEmpty()) {
            IdentityClazz nodeDTO = new IdentityClazz();
            nodeDTO.setId(identityId);
            nodeDTO.setName("identity#" + identityId);
            nodeDTO.setType(ClazzType.I);
            nodeDTO.setEntityClass(null);

            IdentityClazz createdNodeDTO = identityService.createIdentity(nodeDTO);
        }

        var opt2 = identityService.getIdentity(requestedById);

        if (opt2.isEmpty()) {
            IdentityClazz nodeDTO2 = new IdentityClazz();
            nodeDTO2.setId(requestedById);
            nodeDTO2.setName("identity#" + requestedById);
            nodeDTO2.setType(ClazzType.I);
            nodeDTO2.setEntityClass(null);

            IdentityClazz createdNodeDTO2 = identityService.createIdentity(nodeDTO2);
        }

        requestDTO.setIdentityId(requestedById);

        var response = objectService.createObject(applicationId, requestDTO);
        var id = response.getObjectId();


        var OAdto = new OAPropertiesDTO();
        OAdto.setReadProperties(requestDTO.getProperties());
        OAdto.setWriteProperties(requestDTO.getProperties());
        OAdto.setShareReadProperties(requestDTO.getProperties());
        OAdto.setShareWriteProperties(requestDTO.getProperties());

        var result = accessService.createConnection(applicationId, OAdto, id, identityId, requestedById);

        if (result.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final var responseDTO = createResponse(id, identityId, result.get());

        return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
    }

}

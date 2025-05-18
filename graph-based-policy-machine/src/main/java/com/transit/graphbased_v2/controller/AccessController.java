package com.transit.graphbased_v2.controller;

import com.transit.graphbased_v2.controller.dto.*;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.exceptions.ForbiddenException;
import com.transit.graphbased_v2.exceptions.NodeNotFoundException;
import com.transit.graphbased_v2.service.AccessService;
import com.transit.graphbased_v2.service.ApplicationService;
import com.transit.graphbased_v2.transferobjects.AccessTransferComponent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/application/{applicationId}/access")
@Tag(name = "Access", description = "Operations related to access of objects")
public class AccessController {


    @Autowired
    private AccessService accessService;

    @Autowired
    private ApplicationService applicationService;


    @NotNull
    private static AccessResponseDTO createResponse(UUID id, UUID identityId, AccessTransferComponent result) {
        OAPropertiesDTO props = new OAPropertiesDTO(result.getReadProperties(), result.getWriteProperties(), result.getShareReadProperties(), result.getShareWriteProperties(), result.getDigitsAccess());
        var responsevalue = new AccessResponseDTO(id, result.getObjectEntityClazz(), identityId, props);
        return responsevalue;
    }

    @GetMapping("/{objectId}")
    @Operation(summary = "Get access for an object for an identity by objectId")
    public ResponseEntity getAccessById(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID id, @RequestParam(value = "requestedById") UUID requestedById, @RequestParam(value = "identityId", required = false) UUID identityId) throws NodeNotFoundException {

        verifyApplicationExists(applicationId);

        if (identityId == null) {
            identityId = requestedById;
        }
        var result = accessService.getAccess(applicationId, id, identityId, requestedById);
        if (result.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final var responseDTO = createResponse(id, identityId, result.get());

        return new ResponseEntity<>(responseDTO, HttpStatus.OK);


    }

    @GetMapping()
    public ResponseEntity getAccessByList(@PathVariable("applicationId") UUID applicationId, @RequestParam(value = "requestedById", required = true) UUID requestedById, @RequestParam(value = "identityId", required = true) UUID identityId, @RequestBody AccessListDTO accessListDto) {

        verifyApplicationExists(applicationId);


        var aList = accessService.getAccessList(applicationId, accessListDto.getObjectIds(), identityId, requestedById);

        var accessResponseList = new AccessResponseList(aList.stream().map(oa -> createResponse(oa.getObjectId(), oa.getIdentityId(), oa)).toList());

        var responseEntity = new ResponseEntity((accessResponseList), HttpStatus.OK);

        return responseEntity;
    }

    @GetMapping("/search")
    public ResponseEntity getAccessByClass(@PathVariable("applicationId") UUID applicationId, @RequestParam(value = "requestedById") UUID requestedById, @RequestParam(value = "objectEntityClass") String objectEntityClass, @RequestParam(value = "identityId", required = false) UUID identityId, @RequestParam(value = "pagesize", required = false) Integer pagesize, @RequestParam(value = "createdByMyOwn") boolean createdByMyOwn) {


        verifyApplicationExists(applicationId);


        if (pagesize == null) {
            pagesize = 300; // Default value if pagesize is not provided
        } else if (pagesize > 10000) {
            pagesize = 1000; // Limit pagesize if it exceeds 10000
        }

        return new ResponseEntity(new AccessResponseList(accessService.getAccessClazz(applicationId, objectEntityClass, requestedById, createdByMyOwn, identityId, pagesize).stream().map(oa -> createResponse(oa.getObjectId(), oa.getIdentityId(), oa)).toList()), HttpStatus.OK);
    }

    @PutMapping("/{objectId}")
    public ResponseEntity updateRights(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID id, @RequestParam(value = "requestedById") UUID requestedById, @RequestParam(value = "identityId") UUID identityId, @RequestBody OAPropertiesDTO oaPropertiesdto) {

        verifyApplicationExists(applicationId);

        //identities can't control their own rights
        if (requestedById.equals(identityId)) {
            throw new BadRequestException("Cannot update access for own identity.");
        }

        if (oaPropertiesdto.getDigitsAccess() == null) {
            Set<DigitsAccessDTO> x = new HashSet<>();
            oaPropertiesdto.setDigitsAccess(x);
        }
        var result = accessService.updateConnection(applicationId, oaPropertiesdto, id, identityId, requestedById);

        if (result.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final var responseDTO = createResponse(id, identityId, result.get());

        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }

    @PostMapping("/{objectId}")
    public ResponseEntity createRights(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID id, @RequestParam(value = "requestedById") UUID requestedById, @RequestParam(value = "identityId") UUID identityId, @RequestBody OAPropertiesDTO oaPropertiesdto) throws BadRequestException, ForbiddenException {

        verifyApplicationExists(applicationId);

        //identities can't control their own rights
        if (requestedById.equals(identityId)) {
            throw new BadRequestException("Cannot update access for own identity.");
        }

        if (oaPropertiesdto.getDigitsAccess() == null) {
            Set<DigitsAccessDTO> x = new HashSet<>();
            oaPropertiesdto.setDigitsAccess(x);
        }
        var result = accessService.createConnection(applicationId, oaPropertiesdto, id, identityId, requestedById);

        if (result.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        final var responseDTO = createResponse(id, identityId, result.get());

        return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
    }


    @DeleteMapping("/{objectId}")
    public ResponseEntity deleteRights(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID id, @RequestParam(value = "requestedById") UUID requestedById, @RequestParam(value = "identityId") UUID identityId) {

        verifyApplicationExists(applicationId);


        var x = accessService.deleteConnectionRecursive(applicationId, id, identityId, requestedById);
        if (!x) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity(HttpStatus.NO_CONTENT);
    }


    private void verifyApplicationExists(UUID applicationId) {
        var opt = applicationService.getApplication(applicationId);
        if (opt.isEmpty()) {
            throw new BadRequestException("Application with ID " + applicationId + " does not exist.");
        }
    }
}

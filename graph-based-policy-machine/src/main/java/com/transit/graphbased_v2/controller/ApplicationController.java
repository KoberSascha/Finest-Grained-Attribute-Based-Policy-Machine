package com.transit.graphbased_v2.controller;

import com.transit.graphbased_v2.controller.dto.ApplicationDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ApplicationClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.service.ApplicationService;
import com.transit.graphbased_v2.service.impl.AccessServiceBean;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Validator;
import java.util.UUID;

@RestController
@RequestMapping("/application")
@Tag(name = "Application", description = "Operations related to applications")
public class ApplicationController {

    @Autowired
    private Validator validator;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private AccessServiceBean accessServiceBean;


    @PostMapping
    public ResponseEntity<Object> createNode(@RequestBody ApplicationDTO requestDTO) throws BadRequestException {


        if (requestDTO.getApplicationId() == null) {
            throw new BadRequestException("Field 'applicationId' is required.");
        }

        if (requestDTO.getApplicationName() == null || requestDTO.getApplicationName().trim().isEmpty()) {
            throw new BadRequestException("Field 'applicationName' is required and must not be blank.");
        }

        if (requestDTO.getIdentityId() == null) {
            throw new BadRequestException("Field 'identityId' is required.");
        }


        verifyApplicationExists(requestDTO.getApplicationId(), false);


        ApplicationClazz nodeDTO = new ApplicationClazz();
        //set unique ID
        nodeDTO.setId(requestDTO.getApplicationId());
        nodeDTO.setName(requestDTO.getApplicationName());
        nodeDTO.setType(ClazzType.A);
        nodeDTO.setIdentityId(requestDTO.getIdentityId());

        return new ResponseEntity<>(applicationService.createApplication(nodeDTO), HttpStatus.CREATED);
    }


    @GetMapping("/{applicationId}")
    public ResponseEntity getNode(@PathVariable("applicationId") UUID applicationId) {


        var opt = applicationService.getApplication(applicationId);

        if (opt.isEmpty()) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity(opt.get(), HttpStatus.OK);
        }
    }

    @GetMapping
    public ResponseEntity<?> getNodesByIdentity(@RequestParam(value = "identityId", required = false) UUID identityId) {

        var opt = (identityId != null)
                ? applicationService.getApplicationsByIdentity(identityId)
                : applicationService.getApplications();

        if (opt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(opt.get(), HttpStatus.OK);
        }
    }


    @PatchMapping("/{applicationId}")
    public ResponseEntity<Object> updateNode(@PathVariable("applicationId") UUID applicationId, @RequestBody ApplicationDTO requestDTO) throws BadRequestException {

        return new ResponseEntity<>(applicationService.patchApplication(applicationId, requestDTO), HttpStatus.OK);

    }


   /*

    @GetMapping("/{id}")
    public ResponseEntity getNode(@PathVariable("id") UUID id) {


        var opt = objectService.getObject(id);

        if (opt.isEmpty()) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity(opt.get(), HttpStatus.OK);
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity deleteNode(@PathVariable("id") UUID id, @RequestParam(value = "requestedById") UUID requestedById) {

        if (requestedById == null) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        applicationService.deleteObject(id, requestedById);
        return new ResponseEntity(HttpStatus.OK);

    }


*/


    private void verifyApplicationExists(UUID applicationId, boolean shouldExist) throws BadRequestException {
        var opt = applicationService.getApplication(applicationId);

        if (shouldExist) {
            if (opt.isEmpty()) {
                throw new BadRequestException("Application with ID " + applicationId + " does not exist.");
            }
        } else {
            if (!opt.isEmpty()) {
                throw new BadRequestException("Application with ID " + applicationId + " already exist.");
            }
        }
    }


}

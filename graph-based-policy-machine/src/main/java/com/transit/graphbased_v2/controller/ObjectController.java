package com.transit.graphbased_v2.controller;

import com.transit.graphbased_v2.controller.dto.ObjectDTO;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.exceptions.ValidationException;
import com.transit.graphbased_v2.service.ApplicationService;
import com.transit.graphbased_v2.service.ObjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/application/{applicationId}/object")
@Tag(name = "Object", description = "Operations related to objects")
public class ObjectController {

    @Autowired
    private Validator validator;

    @Autowired
    private ObjectService objectService;

    @Autowired
    private ApplicationService applicationService;


    @PostMapping
    public ResponseEntity<Object> createObjectNode(@PathVariable("applicationId") UUID applicationId, @RequestBody ObjectDTO requestDTO) throws BadRequestException {
        applicationService.verifyApplicationExists(applicationId);

        return new ResponseEntity<>(objectService.createObject(applicationId, requestDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{objectId}")
    public ResponseEntity<Object> updateObjectNode(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID objectId, @RequestBody ObjectDTO requestDTO) throws BadRequestException {

        applicationService.verifyApplicationExists(applicationId);

        requestDTO.setObjectId(objectId);
        Set<ConstraintViolation<ObjectDTO>> violations = validator.validate(requestDTO);
        if (!violations.isEmpty()) {
            Set<String> failures = violations.stream().map(contraints -> contraints.getRootBeanClass().getSimpleName() + "." + contraints.getPropertyPath() + " " + contraints.getMessage()).collect(Collectors.toSet());
            String failure = "";
            for (String fail : failures) {
                failure = failure + fail + ",";
            }

            throw new ValidationException(failure);
        }
        return new ResponseEntity<>(objectService.updateObject(applicationId, requestDTO), HttpStatus.OK);

    }


    @GetMapping("/{objectId}")
    public ResponseEntity getObjectNode(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID id) {

        applicationService.verifyApplicationExists(applicationId);

        var opt = objectService.getObject(applicationId, id);

        if (opt.isEmpty()) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity(opt.get(), HttpStatus.OK);
        }
    }


    @DeleteMapping("/{objectId}")
    public ResponseEntity deleteObjectNode(@PathVariable("applicationId") UUID applicationId, @PathVariable("objectId") UUID objectId, @RequestParam(value = "requestedById") UUID requestedById) {


        if (requestedById == null) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        applicationService.verifyApplicationExists(applicationId);

        objectService.deleteObject(applicationId, objectId, requestedById);
        return new ResponseEntity(HttpStatus.OK);

    }


}

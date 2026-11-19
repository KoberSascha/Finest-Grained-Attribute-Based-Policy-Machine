package com.datatrust.graphbased_v2.service.impl;

import com.datatrust.graphbased_v2.controller.dto.ObjectDTO;
import com.datatrust.graphbased_v2.controller.dto.ObjectResponseDTO;
import com.datatrust.graphbased_v2.domain.graph.nodes.ClazzType;
import com.datatrust.graphbased_v2.domain.graph.nodes.EntityClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectClazz;
import com.datatrust.graphbased_v2.domain.graph.relationships.EntityConnection;
import com.datatrust.graphbased_v2.exceptions.*;
import com.datatrust.graphbased_v2.performacelogging.LogExecutionTime;
import com.datatrust.graphbased_v2.repository.*;
import com.datatrust.graphbased_v2.service.ObjectService;
import com.datatrust.graphbased_v2.service.helper.OaDeletionService;
import com.datatrust.graphbased_v2.service.helper.UpdateRightsRecursive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ObjectServiceBean implements ObjectService {

    @Autowired
    private Validator validator;

    @Autowired
    private EntityClazzRepository entityClazzRepository;
    @Autowired
    private ObjectClazzRepository objectClazzRepository;
    @Autowired
    private IdentityClazzRepository identityClazzRepository;
    @Autowired
    private ObjectAttributeClazzRepository objectAttributeClazzRepository;

    @Autowired
    private EntityConnectionRepository entityConnectionRepository;

    @Autowired
    private RelationshipConnectionRepository relationshipConnectionRepository;


    @Autowired
    private AssigmentRepository assigmentRepository;

    @Autowired
    private RightsConnectionRepository rightsConnectionRepository;

    @Autowired
    private RightsRepository rightsRepository;

    @Autowired
    private UpdateRightsRecursive updateRecursive;

    @Autowired
    private OaDeletionService oaDeletionService;


    @Override
    public Optional<ObjectResponseDTO> getObject(UUID applicationId, UUID objectId) {
        return objectClazzRepository.findByApplicationIdAndId(applicationId, objectId)
                .map(oNode -> new ObjectResponseDTO(oNode.getId(), oNode.getEntityClass(), oNode.getName()));
    }


    @LogExecutionTime
    @Override
    public ObjectResponseDTO createObject(UUID applicationId, ObjectDTO dto) throws BadRequestException {
        Set<ConstraintViolation<ObjectDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            Set<String> failures = violations
                    .stream()
                    .map(contraints -> contraints.getRootBeanClass().getSimpleName() +
                            "." + contraints.getPropertyPath() + " " + contraints.getMessage())
                    .collect(Collectors.toSet());
            String failure = "";
            for (String fail : failures) {
                failure = failure + fail + ",";
            }

            throw new ValidationException(failure);
        }

        var objectList = rightsRepository.checkIfAllNodesExistsForObject(applicationId, dto.getIdentityId(), dto.getObjectId(), dto.getObjectEntityClass());

        if (objectList.isEmpty()) {
            throw new NodeNotFoundException("Some error occurred.");
        }

        var objects = objectList.get();
        var objectNode = objects.get(0); // o
        var requestingIdentity = objects.get(1); // i
        var entity = objects.get(2); // e

        if (requestingIdentity.getId() == null) {
            throw new NodeNotFoundException("Identity " + dto.getIdentityId() + " not exists.");
        }

        if (objectNode.getId() != null) {
            throw new NodeIdExistsException(dto.getObjectId());
        }


        EntityClazz entityClassNode = null;
        if (entity.getId() == null) {
            entityClassNode = new EntityClazz();
            entityClassNode.setId(UUID.randomUUID());
            entityClassNode.setEntityClass(dto.getObjectEntityClass());
            entityClassNode.setType(ClazzType.E);
            entityClassNode.setName(dto.getObjectEntityClass());
            //  entityClassNode = entityClazzRepository.save(entityClassNode);

            var eNode = rightsRepository.createEntity(applicationId, entityClassNode);

            if (!eNode) {
                throw new NodeNotFoundException("Some error occurred when creating entity.");
            }
        }

        ObjectClazz oNode = new ObjectClazz();
        oNode.setId(dto.getObjectId());
        oNode.setEntityClass(dto.getObjectEntityClass());
        oNode.setType(ClazzType.O);
        oNode.setName(oNode.getEntityClass() + "#" + oNode.getId());


        ObjectAttributeClazz oaNode = new ObjectAttributeClazz();
        oaNode.setId(UUID.randomUUID());
        oaNode.setName("OA#" + oaNode.getId() + "Identity#" + dto.getIdentityId() + "#" + oNode.getEntityClass() + "#" + oNode.getId());
        oaNode.setType(ClazzType.OA);
        oaNode.setEntityClass("OA");

        //new version
        oaNode.setReadProperties(dto.getProperties());
        oaNode.setWriteProperties(dto.getProperties());
        oaNode.setShareReadProperties(dto.getProperties());
        oaNode.setShareWriteProperties(dto.getProperties());


        var oNode1 = rightsRepository.createObject(applicationId, dto.getIdentityId(), oNode, oaNode);


        /*  objectClazzRepository.save(oNode);
      objectAttributeClazzRepository.save(oaNode);
        var relation = new RelationshipConnection(dto.getIdentityId(), oaNode.getId(), true, true, true);
        relationshipConnectionRepository.createRelationship(relation);



        while (true) {
            try {
                var x = entityClazzRepository.findAllByEntityClass(dto.getObjectEntityClass());
                if (!x.isEmpty()) {
                    entityClassNode = x.get(0);
                }
                assert entityClassNode != null;
                var entityCon = new EntityConnection(entityClassNode.getId(), oNode.getId());
                entityConnectionRepository.createRelationship(entityCon);
                break;
            } catch (Exception ignored) {

            }
        }
        var assignment2 = new Assignment(oNode.getId(), oaNode.getId());
        assigmentRepository.createRelationship(assignment2);

    */
        return new ObjectResponseDTO(oNode.getId(), oNode.getEntityClass(), oNode.getName());
    }


    @Override
    public ObjectResponseDTO updateObject(UUID applicationId, ObjectDTO dto) throws BadRequestException {


        var oldRights = rightsRepository.findOwnedOaId(applicationId, dto.getObjectId(), dto.getIdentityId());
        if (oldRights.isEmpty()) {
            throw new NodeNotFoundException("Cannot find current OA node");
        }
        if (!rightsConnectionRepository.getOutgoingRelationships(oldRights.get()).isEmpty()) {
            throw new BadRequestException("You didn't create this Object.");
        }

        var oaNode = objectAttributeClazzRepository.findById(oldRights.get())
                .orElseThrow(() -> new NodeNotFoundException("Cannot find current OA node"));
        var oNode = objectClazzRepository.findById(dto.getObjectId())
                .orElseThrow(() -> new NodeNotFoundException(dto.getObjectId()));
        var oldEntityConnection = entityConnectionRepository.getIncomingRelationships(dto.getObjectId()).stream()
                .findFirst().orElseThrow(() -> new NodeNotFoundException("Cannot find entity for object"));
        var oldEntity = entityClazzRepository.findById(oldEntityConnection.getSourceID())
                .orElseThrow(() -> new NodeNotFoundException(oldEntityConnection.getSourceID()));
        if (!oldEntity.getEntityClass().equals(dto.getObjectEntityClass())) {
            var targetEntity = entityClazzRepository.findByApplicationIdAndEntityClass(applicationId, dto.getObjectEntityClass());
            EntityClazz entityClassNode;
            if (targetEntity.isPresent()) {
                entityClassNode = targetEntity.get();
            } else {
                entityClassNode = new EntityClazz();
                entityClassNode.setId(UUID.randomUUID());
                entityClassNode.setEntityClass(dto.getObjectEntityClass());
                entityClassNode.setType(ClazzType.E);
                entityClassNode.setName(dto.getObjectEntityClass());
                if (!Boolean.TRUE.equals(rightsRepository.createEntity(applicationId, entityClassNode))) {
                    throw new NodeNotFoundException("Some error occurred when creating entity.");
                }
            }

            // Establish the target connection before removing the object's existing connection.
            entityConnectionRepository.createRelationship(new EntityConnection(entityClassNode.getId(), dto.getObjectId()));
            oNode.setEntityClass(dto.getObjectEntityClass());
            oNode.setName(dto.getObjectEntityClass() + '#' + dto.getObjectId());
            objectClazzRepository.save(oNode);
            entityConnectionRepository.deleteRelationship(oldEntityConnection);
        }

        oaNode.setReadProperties(dto.getProperties());
        oaNode.setWriteProperties(dto.getProperties());
        oaNode.setShareReadProperties(dto.getProperties());
        oaNode.setShareWriteProperties(dto.getProperties());
        objectAttributeClazzRepository.save(oaNode);
        updateRecursive.updateRecursive(oaNode.getId());

        return new ObjectResponseDTO(dto.getObjectId(), dto.getObjectEntityClass(), oNode.getName());

    }


    @Override
    public boolean deleteObject(UUID applicationId, UUID objectId, UUID identityId) throws ForbiddenException, BadRequestException {

        var rights = rightsRepository.findOwnedOaId(applicationId, objectId, identityId);
        if (rights.isEmpty()) {
            throw new BadRequestException("You don't have rights to delete this object");
        } else if (!rightsConnectionRepository.getOutgoingRelationships(rights.get()).isEmpty()) {
            throw new BadRequestException("You don't have rights to delete this object");
        }

        oaDeletionService.deleteSubtree(rights.get());
        entityConnectionRepository.getIncomingRelationships(objectId)
                .forEach(entityConnectionRepository::deleteRelationship);
        objectClazzRepository.deleteById(objectId);
        return true;
    }
}

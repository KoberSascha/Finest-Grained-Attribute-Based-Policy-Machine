package com.transit.graphbased_v2.service.impl;

import com.transit.graphbased_v2.controller.dto.ObjectDTO;
import com.transit.graphbased_v2.controller.dto.ObjectResponseDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.domain.graph.nodes.EntityClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectClazz;
import com.transit.graphbased_v2.domain.graph.relationships.EntityConnection;
import com.transit.graphbased_v2.domain.graph.relationships.Relationship;
import com.transit.graphbased_v2.exceptions.*;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import com.transit.graphbased_v2.repository.*;
import com.transit.graphbased_v2.service.ObjectService;
import com.transit.graphbased_v2.service.helper.UpdateRightsRecursive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.HashSet;
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


    @Override
    public Optional<ObjectResponseDTO> getObject(UUID applicationId, UUID objectId) {
        return objectClazzRepository.findById(objectId).map(oNode -> new ObjectResponseDTO(oNode.getId(), oNode.getEntityClass(), oNode.getName()));
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


        var oldRights = rightsRepository.getRights(applicationId, dto.getIdentityId(), dto.getIdentityId(), dto.getObjectId());
        if (oldRights.isEmpty()) {
            throw new NodeNotFoundException("Cannot find current OA node");
        }
        if (!rightsConnectionRepository.getOutgoingRelationships(oldRights.get().getId()).isEmpty()) {
            throw new BadRequestException("You didn't create this Object.");
        }

        EntityClazz entityClassNode = null;
        if (!entityClazzRepository.existsEntityClazzByEntityClass(dto.getObjectEntityClass())) {
            entityClassNode = new EntityClazz();
            entityClassNode.setId(UUID.randomUUID());
            entityClassNode.setEntityClass(dto.getObjectEntityClass());
            entityClassNode.setType(ClazzType.E);
            entityClassNode.setName(dto.getObjectEntityClass());
            entityClassNode = entityClazzRepository.save(entityClassNode);
        }


        //update OA Node rightsProperties
        var oaNode = objectAttributeClazzRepository.findById(oldRights.get().getId()).get();

        //new version
        oaNode.setReadProperties(dto.getProperties());
        oaNode.setWriteProperties(dto.getProperties());
        oaNode.setShareReadProperties(dto.getProperties());
        oaNode.setShareWriteProperties(dto.getProperties());

        objectAttributeClazzRepository.save(oaNode);

        var oldEntityConnection = entityConnectionRepository.getIncomingRelationships(dto.getObjectId()).get(0);
        var oldEntity = entityClazzRepository.findById(oldEntityConnection.getSourceID()).get();
        if (!oldEntity.getEntityClass().equals(dto.getObjectEntityClass())) {

            //O Node entityClass and name by newEntityClass
            var oNode = objectClazzRepository.findById(dto.getObjectId()).get();
            oNode.setEntityClass(dto.getObjectEntityClass());
            oNode.setName(dto.getObjectEntityClass() + '#' + dto.getObjectId());
            objectClazzRepository.save(oNode);

            //Connect to the right E Node (Enity Node)
            entityConnectionRepository.deleteRelationship(oldEntityConnection);
            while (true) {
                try {
                    var x = entityClazzRepository.findAllByEntityClass(dto.getObjectEntityClass());
                    if (!x.isEmpty()) {
                        entityClassNode = x.get(0);
                    }
                    assert entityClassNode != null;
                    var entityCon = new EntityConnection(entityClassNode.getId(), dto.getObjectId());
                    entityConnectionRepository.createRelationship(entityCon);


                    break;
                } catch (Exception ignored) {

                }
            }
        }

        updateRecursive.updateRecursive(oaNode.getId());

        return new ObjectResponseDTO(dto.getObjectId(), dto.getObjectEntityClass(), objectClazzRepository.findById(dto.getObjectId()).get().getName());

    }


    @Override
    public boolean deleteObject(UUID applicationId, UUID objectId, UUID identityId) throws ForbiddenException, BadRequestException {

        var rights = rightsRepository.getRights(applicationId, identityId, identityId, objectId);
        if (rights.isEmpty()) {
            throw new BadRequestException("You don't have rights to delete this object");
        } else if (!rightsConnectionRepository.getOutgoingRelationships(rights.get().getId()).isEmpty()) {
            throw new BadRequestException("You don't have rights to delete this object");
        }

        var connectedOAnodes = new HashSet<UUID>();
        long sizeList = 0;
        connectedOAnodes.add(rights.get().getId());

        while (sizeList < connectedOAnodes.size()) {
            sizeList = connectedOAnodes.size();
            connectedOAnodes.forEach(id -> {
                        connectedOAnodes.addAll(rightsConnectionRepository.getIncomingRelationships(id).stream().map(Relationship::getSourceID).toList());
                    }
            );
            deleteOaNodes(connectedOAnodes);
            objectClazzRepository.deleteById(objectId);
        }
        return true;
    }

    private void deleteOaNodes(HashSet<UUID> connectedOAnodes) {
        while (!connectedOAnodes.isEmpty()) {
            connectedOAnodes.forEach(id -> {
                if (rightsConnectionRepository.getIncomingRelationships(id).isEmpty()) {
                    relationshipConnectionRepository.getIncomingRelationships(id).forEach(assoc -> relationshipConnectionRepository.deleteRelationship(assoc));
                    assigmentRepository.getIncomingRelationships(id).forEach(assign -> {
                        entityConnectionRepository.getIncomingRelationships(assign.getSourceID()).forEach(assignToEntity -> entityConnectionRepository.deleteRelationship(assignToEntity));
                        assigmentRepository.deleteRelationship(assign);
                    });
                    rightsConnectionRepository.getOutgoingRelationships(id).forEach(assoc -> rightsConnectionRepository.deleteRelationship(assoc));
                    objectAttributeClazzRepository.deleteById(id);
                    connectedOAnodes.remove(id);
                }
            });
        }
    }
}

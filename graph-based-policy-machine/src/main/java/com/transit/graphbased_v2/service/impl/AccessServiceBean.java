package com.transit.graphbased_v2.service.impl;

import com.transit.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.transit.graphbased_v2.controller.dto.OAPropertiesDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.transit.graphbased_v2.domain.graph.relationships.Relationship;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.exceptions.ForbiddenException;
import com.transit.graphbased_v2.exceptions.NodeNotFoundException;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import com.transit.graphbased_v2.repository.*;
import com.transit.graphbased_v2.service.AccessService;
import com.transit.graphbased_v2.service.helper.AccessTransferComponentHelper;
import com.transit.graphbased_v2.service.helper.AccessValidator;
import com.transit.graphbased_v2.service.helper.DADeserializer;
import com.transit.graphbased_v2.service.helper.UpdateRightsRecursive;
import com.transit.graphbased_v2.transferobjects.AccessTransferComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AccessServiceBean implements AccessService {

    @Autowired
    private RightsRepository rightsRepository;

    @Autowired
    private IdentityClazzRepository identityClazzRepository;

    @Autowired
    private ObjectClazzRepository objectClazzRepository;

    @Autowired
    private ObjectAttributeClazzRepository objectAttributeClazzRepository;

    @Autowired
    private RightsConnectionRepository rightsConnectionRepository;

    @Autowired
    private EntityConnectionRepository entityConnectionRepository;

    @Autowired
    private RelationshipConnectionRepository relationshipConnectionRepository;


    @Autowired
    private AssigmentRepository assigmentRepository;

    @Autowired
    private EntityClazzRepository entityClazzRepository;


    @Autowired
    private AccessValidator accessValidator;

    @Autowired
    private AccessTransferComponentHelper accessTransferComponentHelper;

    @Autowired
    private UpdateRightsRecursive updateRecursive;


    @Autowired
    private DADeserializer dADeserializer;

    @LogExecutionTime
    @Override
    public Optional<AccessTransferComponent> getAccess(UUID applicationId, UUID oId, UUID identityId, UUID requestedById) throws NodeNotFoundException {


        var rights = rightsRepository.getRightsWithDigitAccess(applicationId, identityId, requestedById, oId);

        if (rights.isEmpty()) {
            throw new NodeNotFoundException(oId);
        }

        return rights.map(entry -> accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId));

    }


    @Override
    public List<AccessTransferComponent> getAccessList(UUID applicationId, Set<UUID> objectIds, UUID identityId, UUID requestedById) {
        return rightsRepository.getRightsListOld(applicationId, objectIds, identityId, requestedById).stream().map(entry -> accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId)).collect(Collectors.toList());
    }

    @Override
    public List<AccessTransferComponent> getAccessClazz(UUID applicationId, String entityClazz, UUID requestedById, boolean createdByMyOwn, UUID identityId, Integer pagesize) {

        return rightsRepository.getRightsClass(entityClazz, requestedById, createdByMyOwn, identityId, pagesize).stream().map(entry -> {

            if (identityId == null) {
                return accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), requestedById);
            } else {
                return accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId);
            }
        }).collect(Collectors.toList());
    }


    @LogExecutionTime
    @Override
    public Optional<AccessTransferComponent> createConnection(UUID applicationId, OAPropertiesDTO oaPropertiesdto, UUID oId, UUID identityId, UUID requestedById) throws BadRequestException, ForbiddenException {

        //collection parentOA, childOA, object and idenities
        var objects = getRequiredObjectsForRequest(applicationId, oaPropertiesdto, oId, identityId, requestedById, false);
        var requestingIdentityRights = objects.get(3);

        ObjectAttributeClazz oaNodeTmp = new ObjectAttributeClazz();
        oaNodeTmp.setId(UUID.randomUUID());
        oaNodeTmp.setName("OA#" + oaNodeTmp.getId() + "Group#" + identityId + "#" + oId);
        oaNodeTmp.setType(ClazzType.OA);
        oaNodeTmp.setEntityClass("OA");
        ObjectAttributeClazz oaNode = accessValidator.generateValidatedOANode(oaNodeTmp, oaPropertiesdto, requestingIdentityRights);
        var newOA = rightsRepository.createAccess(applicationId, oaNode, requestingIdentityRights.getId(), identityId, requestedById, oId);

        //get digitAccess for parent
        var daParent = rightsRepository.getDigitAccess(requestingIdentityRights.getId());
        Set<DigitsAccessDTO> daParentList = new HashSet<>();
        daParent.ifPresent(da -> {
            daParentList.addAll(dADeserializer.parseDigitsAccessFromDa(da));
        });

        if (!oaPropertiesdto.getDigitsAccess().isEmpty() || !daParentList.isEmpty()) {
            if (newOA.isPresent()) {
                var validateRightsPropertiesDigitAccess = accessValidator.validateRightsPropertiesDigitAccessAgainstParentOa(oaPropertiesdto.getDigitsAccess(), daParentList, newOA.get());


                //Now delete all DA-Nodes and add and connect the new validated DA-Nodes
                rightsRepository.createDigitAccess(validateRightsPropertiesDigitAccess, oaNodeTmp.getId());


                newOA.get().setDigitsAccess(validateRightsPropertiesDigitAccess);
            }
        }

        return newOA.map(entry -> accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId));
    }

    @Override
    public Optional<AccessTransferComponent> updateConnection(UUID applicationId, OAPropertiesDTO oaPropertiesdto, UUID oId, UUID identityId, UUID requestedById) throws BadRequestException, ForbiddenException {

        //collection parentOA, childOA, object and idenities
        var objects = getRequiredObjectsForRequest(applicationId, oaPropertiesdto, oId, identityId, requestedById, true);
        var requestingIdentityRights = objects.get(3);
        var updatingIdentityRights = objects.get(4);

        var currentOa = updatingIdentityRights;
        var oaNodeTmp = new ObjectAttributeClazz();
        oaNodeTmp.setId(currentOa.getId());
        oaNodeTmp.setLabels(currentOa.getLabels());
        oaNodeTmp.setEntityClass(currentOa.getEntityClass());
        oaNodeTmp.setType(currentOa.getType());
        oaNodeTmp.setName(currentOa.getName());

        ObjectAttributeClazz oaNode = accessValidator.generateValidatedOANode(oaNodeTmp, oaPropertiesdto, requestingIdentityRights);

        //get digitAccess for parent
        var daParent = rightsRepository.getDigitAccess(requestingIdentityRights.getId());
        Set<DigitsAccessDTO> daParentList = new HashSet<>();
        daParent.ifPresent(da -> {
            daParentList.addAll(dADeserializer.parseDigitsAccessFromDa(da));
        });

        //get digitAccess for child
        var daChild = rightsRepository.getDigitAccess(updatingIdentityRights.getId());
        Set<DigitsAccessDTO> daChildList = new HashSet<>();
        daChild.ifPresent(da -> {
            daChildList.addAll(dADeserializer.parseDigitsAccessFromDa(da));
        });


        var updatedOa = rightsRepository.updateOa(oaNode);


        // if digitsAccess is passed via request or parentOa has already digitAccess, then validate digitAccess for the updating OA
        if (!oaPropertiesdto.getDigitsAccess().isEmpty() || !daParentList.isEmpty()) {

            if (updatedOa.isPresent()) {
                var validateRightsPropertiesDigitAccess = accessValidator.validateRightsPropertiesDigitAccessAgainstParentOa(oaPropertiesdto.getDigitsAccess(), daParentList, updatedOa.get());


                //Now delete all DA-Nodes and add and connect the new validated DA-Nodes
                rightsRepository.createDigitAccess(validateRightsPropertiesDigitAccess, oaNodeTmp.getId());


                updatedOa.get().setDigitsAccess(validateRightsPropertiesDigitAccess);
            }


        }

        updateRecursive.updateRecursive(oaNode.getId());

        return updatedOa.map(entry -> accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId));
    }


    @Override
    public boolean deleteConnectionRecursive(UUID applicationId, UUID oId, UUID identityId, UUID requestedById) {
        var rights = rightsRepository.getRights(applicationId, identityId, requestedById, oId);
        if (rights.isEmpty()) {
            return false;
        }
        var oaId = rights.get().getId();

        long sizeList = 0;

        var connectedOAnodes = new HashSet<UUID>();
        connectedOAnodes.add(rights.get().getId());

        while (sizeList < connectedOAnodes.size()) {
            sizeList = connectedOAnodes.size();
            connectedOAnodes.forEach(id -> {
                connectedOAnodes.addAll(rightsConnectionRepository.getIncomingRelationships(id).stream().map(Relationship::getSourceID).toList());
            });
        }
        deleteOaNodes(connectedOAnodes);
        return true;
    }

    public void deleteOaNodes(HashSet<UUID> connectedOAnodes) {
        while (!connectedOAnodes.isEmpty()) {
            connectedOAnodes.forEach(id -> {
                if (rightsConnectionRepository.getIncomingRelationships(id).isEmpty()) {
                    relationshipConnectionRepository.getIncomingRelationships(id).forEach(assoc -> relationshipConnectionRepository.deleteRelationship(assoc));
                    assigmentRepository.getIncomingRelationships(id).forEach(assign -> assigmentRepository.deleteRelationship(assign));
                    rightsConnectionRepository.getOutgoingRelationships(id).forEach(assoc -> rightsConnectionRepository.deleteRelationship(assoc));
                    objectAttributeClazzRepository.deleteById(id);
                    connectedOAnodes.remove(id);
                }

            });
        }

    }


    public List<ObjectAttributeExtendedClazz> getRequiredObjectsForRequest(UUID applicationId, OAPropertiesDTO oaPropertiesdto, UUID oId, UUID identityId, UUID requestedById, boolean oAShouldExists) {


        Set<String> readProperties = oaPropertiesdto.getReadProperties();
        Set<String> writeProperties = oaPropertiesdto.getWriteProperties();
        Set<String> shareReadProperties = oaPropertiesdto.getShareReadProperties();
        Set<String> shareWriteProperties = oaPropertiesdto.getShareWriteProperties();

        if (readProperties == null) {
            readProperties = new HashSet<>();
        }
        if (writeProperties == null) {
            writeProperties = new HashSet<>();
        }
        if (shareReadProperties == null) {
            shareReadProperties = new HashSet<>();
        }
        if (shareWriteProperties == null) {
            shareWriteProperties = new HashSet<>();
        }

        if (readProperties.size() < writeProperties.size()) {
            throw new BadRequestException("Cannot have more write Properties as read Properties.");
        }

        if (readProperties.size() < shareReadProperties.size()) {
            throw new BadRequestException("Cannot have more sharing read Properties as read Properties.");
        }

        if (writeProperties.size() < shareWriteProperties.size()) {
            throw new BadRequestException("Cannot have more sharing write Properties as write Properties.");
        }


        var objectList = rightsRepository.checkIfAllNodesExistsForAccess(applicationId, identityId, requestedById, oId);

        if (objectList.isEmpty()) {
            throw new NodeNotFoundException("Some error occurred.");
        }

        var objects = objectList.get();
        var objectNode = objects.get(0);
        var requestingIdentity = objects.get(1);
        var updatingIdentity = objects.get(2);
        var requestingIdentityRights = objects.get(3);
        var updatingIdentityRights = objects.get(4);

        if (objectNode.getId() == null) {
            throw new NodeNotFoundException("Object not exists");
        }

        if (requestingIdentity.getId() == null) {
            throw new NodeNotFoundException("Identity " + requestedById + " not exists.");
        }

        if (updatingIdentity.getId() == null) {
            throw new NodeNotFoundException("Identity " + identityId + " not exists.");
        }

        if (requestingIdentityRights.getId() == null) {
            throw new BadRequestException("No Access");
        }

        if (requestingIdentityRights.getId() == null) {
            throw new BadRequestException("No Access");
        }


        if (oAShouldExists) {
            if (updatingIdentityRights.getId() == null) {
                throw new BadRequestException("Cannot find existing access data for this identity.");
            }
        } else {
            if (updatingIdentityRights.getId() != null) {
                throw new BadRequestException("Rights already exists--> Have to update not create.");
            }
        }


        return objects;
    }

}

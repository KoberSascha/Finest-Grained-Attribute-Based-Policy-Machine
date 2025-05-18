package com.transit.graphbased_v2.service.impl;

import com.transit.graphbased_v2.controller.dto.ApplicationDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ClazzType;
import com.transit.graphbased_v2.domain.graph.nodes.IdentityClazz;
import com.transit.graphbased_v2.exceptions.ForbiddenException;
import com.transit.graphbased_v2.exceptions.NodeIdExistsException;
import com.transit.graphbased_v2.exceptions.NodeNotFoundException;
import com.transit.graphbased_v2.repository.IdentityClazzRepository;
import com.transit.graphbased_v2.repository.RelationshipConnectionRepository;
import com.transit.graphbased_v2.repository.RightsRepository;
import com.transit.graphbased_v2.service.AccessService;
import com.transit.graphbased_v2.service.ApplicationService;
import com.transit.graphbased_v2.service.IdentityService;
import com.transit.graphbased_v2.service.ObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdentityServiceBean implements IdentityService {

    @Autowired
    private IdentityClazzRepository identityClazzRepository;

    @Autowired
    private RightsRepository rightsRepository;
    @Autowired
    private RelationshipConnectionRepository relationshipConnectionRepository;

    @Autowired
    private ObjectService objectService;
    @Autowired
    private AccessService accessService;
    @Autowired
    private ApplicationService applicationService;


    @Override
    public IdentityClazz createIdentity(IdentityClazz userAttributeClazz) throws NodeIdExistsException {
        if (identityClazzRepository.existsById(userAttributeClazz.getId())) {
            throw new NodeIdExistsException(userAttributeClazz.getId());
        }
        userAttributeClazz.setType(ClazzType.I);
        userAttributeClazz.setName("identity#" + userAttributeClazz.getId());
        userAttributeClazz.setEntityClass(null);
        userAttributeClazz.setProperties(new HashMap<>());
        return identityClazzRepository.save(userAttributeClazz);

    }

    @Override
    public Optional<IdentityClazz> getIdentity(UUID id) {
        var temp = identityClazzRepository.findById(id);
        if (temp.isEmpty()) {
            return Optional.empty();
        } else if (!temp.get().getType().equals(ClazzType.I)) {
            return Optional.empty();
        }
        return temp;
    }

    @Override
    public boolean deleteIdentity(UUID id) throws NodeNotFoundException {
        if (!identityClazzRepository.existsById(id)) {
            throw new NodeNotFoundException(id);
        } else {
            //Delete all nodes and subrights

            rightsRepository.getAllMyRights(id).forEach(oaNode -> {
                var rel = relationshipConnectionRepository.getRelationship(id, oaNode.getId()).get();

                Optional<ApplicationDTO> applicationOpt = applicationService.getApplicationByOANode(oaNode);

                if (applicationOpt.isEmpty()) {
                    throw new RuntimeException("No Application node found for OA");
                }

                ApplicationDTO applicationNode = applicationOpt.get();


                if (rel.isOwns()) {
                    try {


                        objectService.deleteObject(applicationNode.getApplicationId(), oaNode.getOId(), id);


                    } catch (ForbiddenException e) {
                        throw new ForbiddenException(e.getMessage());
                    }
                } else {
                    accessService.deleteConnectionRecursive(applicationNode.getApplicationId(), oaNode.getOId(), id, id);
                }
            });
            identityClazzRepository.deleteById(id);
        }
        return true;
    }
}

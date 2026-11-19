package com.datatrust.graphbased_v2.service.impl;

import com.datatrust.graphbased_v2.controller.dto.ApplicationDTO;
import com.datatrust.graphbased_v2.domain.graph.nodes.ClazzType;
import com.datatrust.graphbased_v2.domain.graph.nodes.IdentityClazz;
import com.datatrust.graphbased_v2.exceptions.ForbiddenException;
import com.datatrust.graphbased_v2.exceptions.NodeIdExistsException;
import com.datatrust.graphbased_v2.exceptions.NodeNotFoundException;
import com.datatrust.graphbased_v2.repository.IdentityClazzRepository;
import com.datatrust.graphbased_v2.repository.RelationshipConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import com.datatrust.graphbased_v2.service.helper.OaDeletionService;
import com.datatrust.graphbased_v2.service.ApplicationService;
import com.datatrust.graphbased_v2.service.IdentityService;
import com.datatrust.graphbased_v2.service.ObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
    private OaDeletionService oaDeletionService;
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
        if (!identityClazzRepository.existsById(id)) throw new NodeNotFoundException(id);
        Map<UUID, UUID> ownedObjects = new LinkedHashMap<>();
        Map<UUID, UUID> receivedRoots = new LinkedHashMap<>();
        // Snapshot every OA separately, and resolve ownership before any subtree is removed.
        // An owner may also receive a delegated grant on the same object via another identity.
        for (var oa : rightsRepository.getAllMyRights(id)) {
            var relationship = relationshipConnectionRepository.getRelationship(id, oa.getId());
            if (relationship.isEmpty()) continue;
            if (relationship.get().isOwns()) {
                var application = applicationService.getApplicationByOANode(oa)
                        .orElseThrow(() -> new NodeNotFoundException("No Application node found for OA"));
                ownedObjects.put(oa.getOId(), application.getApplicationId());
            } else {
                receivedRoots.put(oa.getId(), oa.getOId());
            }
        }
        for (var object : ownedObjects.entrySet())
            objectService.deleteObject(object.getValue(), object.getKey(), id);
        Set<UUID> roots = new LinkedHashSet<>();
        receivedRoots.forEach((oa, object) -> {
            if (!ownedObjects.containsKey(object)) roots.add(oa);
        });
        if (!roots.isEmpty()) oaDeletionService.deleteSubtrees(roots);
        identityClazzRepository.deleteById(id);
        return true;
    }
}

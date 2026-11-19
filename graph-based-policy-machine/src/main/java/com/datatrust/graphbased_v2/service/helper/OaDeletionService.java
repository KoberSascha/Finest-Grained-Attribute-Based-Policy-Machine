package com.datatrust.graphbased_v2.service.helper;

import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import com.datatrust.graphbased_v2.repository.AssigmentRepository;
import com.datatrust.graphbased_v2.repository.ObjectAttributeClazzRepository;
import com.datatrust.graphbased_v2.repository.RelationshipConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deletes access grants and their descendants without deleting objects or entities. */
@Service
public class OaDeletionService {

    private final RightsConnectionRepository rightsConnectionRepository;
    private final RelationshipConnectionRepository relationshipConnectionRepository;
    private final AssigmentRepository assigmentRepository;
    private final RightsRepository rightsRepository;
    private final ObjectAttributeClazzRepository objectAttributeClazzRepository;

    public OaDeletionService(RightsConnectionRepository rightsConnectionRepository,
                             RelationshipConnectionRepository relationshipConnectionRepository,
                             AssigmentRepository assigmentRepository,
                             RightsRepository rightsRepository,
                             ObjectAttributeClazzRepository objectAttributeClazzRepository) {
        this.rightsConnectionRepository = rightsConnectionRepository;
        this.relationshipConnectionRepository = relationshipConnectionRepository;
        this.assigmentRepository = assigmentRepository;
        this.rightsRepository = rightsRepository;
        this.objectAttributeClazzRepository = objectAttributeClazzRepository;
    }

    public void deleteSubtree(UUID rootId) {
        deleteSubtrees(Set.of(rootId));
    }

    public void deleteSubtrees(Set<UUID> rootIds) {
        Map<UUID, Set<UUID>> childrenByParent = collectDescendants(rootIds);
        List<UUID> deletionOrder = orderLeavesFirst(childrenByParent);

        // Validate the complete graph before performing the first write.
        for (UUID id : deletionOrder) {
            deleteNode(id);
        }
    }

    private Map<UUID, Set<UUID>> collectDescendants(Set<UUID> rootIds) {
        Map<UUID, Set<UUID>> childrenByParent = new LinkedHashMap<>();
        Set<UUID> scheduled = new HashSet<>(rootIds);
        Deque<UUID> pending = new ArrayDeque<>(rootIds);

        while (!pending.isEmpty()) {
            UUID parentId = pending.removeFirst();
            Set<UUID> children = new LinkedHashSet<>();
            for (var relationship : rightsConnectionRepository.getIncomingRelationships(parentId)) {
                UUID childId = relationship.getSourceID();
                children.add(childId);
                if (scheduled.add(childId)) {
                    pending.addLast(childId);
                }
            }
            childrenByParent.put(parentId, children);
        }
        return childrenByParent;
    }

    private List<UUID> orderLeavesFirst(Map<UUID, Set<UUID>> childrenByParent) {
        Map<UUID, Integer> remainingChildren = new HashMap<>();
        Map<UUID, Set<UUID>> parentsByChild = new HashMap<>();
        Deque<UUID> leaves = new ArrayDeque<>();

        for (var entry : childrenByParent.entrySet()) {
            UUID parentId = entry.getKey();
            remainingChildren.put(parentId, entry.getValue().size());
            if (entry.getValue().isEmpty()) {
                leaves.addLast(parentId);
            }
            for (UUID childId : entry.getValue()) {
                parentsByChild.computeIfAbsent(childId, ignored -> new LinkedHashSet<>()).add(parentId);
            }
        }

        List<UUID> deletionOrder = new ArrayList<>(childrenByParent.size());
        while (!leaves.isEmpty()) {
            UUID id = leaves.removeFirst();
            deletionOrder.add(id);
            for (UUID parentId : parentsByChild.getOrDefault(id, Set.of())) {
                int count = remainingChildren.compute(parentId, (ignored, current) -> current - 1);
                if (count == 0) {
                    leaves.addLast(parentId);
                }
            }
        }

        if (deletionOrder.size() != childrenByParent.size()) {
            throw new BadRequestException("Cannot delete cyclic access rights graph.");
        }
        return deletionOrder;
    }

    private void deleteNode(UUID id) {
        // Repository implementations may return mutable backing collections.
        for (var relationship : new ArrayList<>(relationshipConnectionRepository.getIncomingRelationships(id))) {
            relationshipConnectionRepository.deleteRelationship(relationship);
        }
        for (var assignment : new ArrayList<>(assigmentRepository.getIncomingRelationships(id))) {
            assigmentRepository.deleteRelationship(assignment);
        }
        for (var relationship : new ArrayList<>(rightsConnectionRepository.getOutgoingRelationships(id))) {
            rightsConnectionRepository.deleteRelationship(relationship);
        }
        rightsRepository.deleteDigitAccess(id);
        objectAttributeClazzRepository.deleteById(id);
    }
}

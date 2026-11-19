package com.datatrust.graphbased_v2.service.helper;

import com.datatrust.graphbased_v2.repository.RightsConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class UpdateRightsRecursive {

    private final RightsConnectionRepository rightsConnectionRepository;
    private final RightsRepository rightsRepository;
    private final AccessValidator accessValidator;
    private final OADeserializer oADeserializer;

    public UpdateRightsRecursive(RightsConnectionRepository rightsConnectionRepository,
                                 RightsRepository rightsRepository,
                                 AccessValidator accessValidator,
                                 OADeserializer oADeserializer) {
        this.rightsConnectionRepository = rightsConnectionRepository;
        this.rightsRepository = rightsRepository;
        this.accessValidator = accessValidator;
        this.oADeserializer = oADeserializer;
    }

    public boolean updateRecursive(UUID startOANode) {
        var childrenByParent = collectDescendants(startOANode);
        // Validate the complete reachable hierarchy before making any changes.
        var parentOrder = parentsBeforeChildren(childrenByParent);

        for (var parentId : parentOrder) {
            var parent = rightsRepository.findOaByIdWithDigitAccess(parentId).get();
            for (var childId : childrenByParent.get(parentId)) {
                var child = rightsRepository.findOaByIdWithDigitAccess(childId).get();

                accessValidator.constrainDelegation(child, oADeserializer.parseOAtoOAExtended(parent));
                rightsRepository.updateOa(child);
            }
        }

        return true;
    }

    private Map<UUID, Set<UUID>> collectDescendants(UUID startOANode) {
        Map<UUID, Set<UUID>> childrenByParent = new LinkedHashMap<>();
        var pending = new ArrayDeque<UUID>();
        childrenByParent.put(startOANode, new LinkedHashSet<>());
        pending.add(startOANode);

        while (!pending.isEmpty()) {
            var parentId = pending.removeFirst();
            var children = childrenByParent.get(parentId);
            for (var relationship : rightsConnectionRepository.getIncomingRelationships(parentId)) {
                var childId = relationship.getSourceID();
                children.add(childId);
                if (!childrenByParent.containsKey(childId)) {
                    childrenByParent.put(childId, new LinkedHashSet<>());
                    pending.addLast(childId);
                }
            }
        }

        return childrenByParent;
    }

    private List<UUID> parentsBeforeChildren(Map<UUID, Set<UUID>> childrenByParent) {
        Map<UUID, Integer> remainingParents = new HashMap<>();
        childrenByParent.keySet().forEach(id -> remainingParents.put(id, 0));
        childrenByParent.values().forEach(children ->
                children.forEach(child -> remainingParents.merge(child, 1, Integer::sum)));

        var ready = new ArrayDeque<UUID>();
        childrenByParent.keySet().stream()
                .filter(id -> remainingParents.get(id) == 0)
                .forEach(ready::addLast);
        List<UUID> order = new ArrayList<>();

        while (!ready.isEmpty()) {
            var parentId = ready.removeFirst();
            order.add(parentId);
            for (var childId : childrenByParent.get(parentId)) {
                if (remainingParents.merge(childId, -1, Integer::sum) == 0) {
                    ready.addLast(childId);
                }
            }
        }

        if (order.size() != childrenByParent.size()) {
            throw new IllegalStateException("Cycle detected in OA rights hierarchy.");
        }
        return order;
    }
}

package com.datatrust.graphbased_v2.service.helper;

import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.datatrust.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.datatrust.graphbased_v2.controller.dto.ReadAbleDigitsDTO;
import com.datatrust.graphbased_v2.domain.graph.relationships.RightsConnection;
import com.datatrust.graphbased_v2.repository.RightsConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class UpdateRightsRecursiveTest {

    private RightsConnectionRepository connections;
    private RightsRepository rights;
    private UpdateRightsRecursive updater;
    private final Map<UUID, ObjectAttributeClazz> nodes = new HashMap<>();
    private final Map<UUID, List<RightsConnection>> children = new HashMap<>();

    @BeforeEach
    void setUp() {
        connections = mock(RightsConnectionRepository.class);
        rights = mock(RightsRepository.class);
        updater = new UpdateRightsRecursive(connections, rights, new AccessValidator(), new OADeserializer());
        when(connections.getIncomingRelationships(any(UUID.class)))
                .thenAnswer(invocation -> children.getOrDefault(invocation.getArgument(0), List.of()));
        when(rights.findOaByIdWithDigitAccess(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(nodes.get(invocation.getArgument(0))));
    }

    @Test
    void restrictsEveryDescendantAcrossFiveDelegationLevels() {
        var root = node("read", "write", "shareRead", "shareWrite");
        root.setWriteProperties(new HashSet<>(Set.of("write", "shareWrite")));
        root.setShareReadProperties(new HashSet<>(Set.of("shareRead", "shareWrite")));
        root.setShareWriteProperties(new HashSet<>(Set.of("shareWrite")));

        var descendants = new ArrayList<ObjectAttributeClazz>();
        var parent = root;
        for (int level = 0; level < 5; level++) {
            var child = node("read", "write", "shareRead", "shareWrite", "revoked");
            connect(parent, child);
            descendants.add(child);
            parent = child;
        }

        assertTrue(updater.updateRecursive(root.getId()));

        for (var descendant : descendants) {
            assertEquals(root.getShareReadProperties(), descendant.getReadProperties());
            assertEquals(root.getShareWriteProperties(), descendant.getWriteProperties());
            assertEquals(root.getShareReadProperties(), descendant.getShareReadProperties());
            assertEquals(root.getShareWriteProperties(), descendant.getShareWriteProperties());
            verify(rights).updateOa(descendant);
        }
        verify(rights, never()).updateOa(root);
    }

    @Test
    void propagatesEachBranchesOwnRestrictionsToItsDescendants() {
        var root = node("common", "branch");
        var left = node("common");
        var right = node("common", "branch");
        var leftChild = node("common", "branch", "revoked");
        var leftGrandchild = node("common", "branch", "revoked");
        var rightChild = node("common", "branch", "revoked");
        connect(root, left);
        connect(root, right);
        connect(left, leftChild);
        connect(leftChild, leftGrandchild);
        connect(right, rightChild);

        updater.updateRecursive(root.getId());

        assertEquals(Set.of("common"), leftChild.getReadProperties());
        assertEquals(Set.of("common"), leftGrandchild.getReadProperties());
        assertEquals(Set.of("common", "branch"), rightChild.getReadProperties());
        for (var descendant : List.of(left, right, leftChild, leftGrandchild, rightChild)) {
            verify(rights).updateOa(descendant);
        }
    }

    @Test
    void doesNotGrantNewParentFieldsToExistingDescendants() {
        var root = node("existing", "new");
        var child = node("existing");
        var grandchild = node("existing");
        connect(root, child);
        connect(child, grandchild);

        updater.updateRecursive(root.getId());

        for (var descendant : List.of(child, grandchild)) {
            assertEquals(Set.of("existing"), descendant.getReadProperties());
            assertEquals(Set.of("existing"), descendant.getWriteProperties());
            assertEquals(Set.of("existing"), descendant.getShareReadProperties());
            assertEquals(Set.of("existing"), descendant.getShareWriteProperties());
        }
    }

    @Test
    void rejectsCyclesBeforeChangingAnyRights() {
        var root = node("allowed");
        var child = node("allowed", "revoked");
        connect(root, child);
        connect(child, root);

        var error = assertThrows(IllegalStateException.class, () -> updater.updateRecursive(root.getId()));

        assertTrue(error.getMessage().contains("Cycle"));
        verifyNoInteractions(rights);
    }

    @Test
    void waitsForAllReachableParentsBeforePropagatingSharedDescendant() {
        var root = node("left", "common", "right");
        var left = node("left", "common");
        var right = node("common", "right");
        var shared = node("left", "common", "right");
        var leaf = node("left", "common", "right");
        connect(root, left);
        connect(root, right);
        connect(left, shared);
        connect(right, shared);
        connect(shared, leaf);

        updater.updateRecursive(root.getId());

        assertEquals(Set.of("common"), shared.getReadProperties());
        assertEquals(Set.of("common"), leaf.getReadProperties());
        verify(rights, times(2)).updateOa(shared);
        verify(rights).updateOa(leaf);
    }

    @Test
    void persistsNarrowedDigitsBeforeReloadingEachFollowingDelegationLevel() {
        var root = node("code");
        root.setDigitsAccess(Set.of(new DigitsAccessDTO("code", Set.of(new ReadAbleDigitsDTO(3, 5)), "readProperties"),
                new DigitsAccessDTO("code", Set.of(new ReadAbleDigitsDTO(3, 5)), "shareReadProperties")));
        var parent = root;
        var descendants = new ArrayList<UUID>();
        for (int level = 0; level < 5; level++) {
            var child = node("code");
            child.setDigitsAccess(Set.of(new DigitsAccessDTO("code", Set.of(new ReadAbleDigitsDTO(1, 8)), "readProperties"),
                    new DigitsAccessDTO("code", Set.of(new ReadAbleDigitsDTO(1, 8)), "shareReadProperties")));
            descendants.add(child.getId());
            connect(parent, child);
            parent = child;
        }
        // Return detached snapshots, as Neo4j does: subsequent reads only see persisted changes.
        when(rights.findOaByIdWithDigitAccess(any(UUID.class))).thenAnswer(invocation ->
                Optional.of(copy(nodes.get(invocation.getArgument(0)))));
        doAnswer(invocation -> {
            var saved = copy(invocation.getArgument(0));
            nodes.put(saved.getId(), saved);
            return Optional.of(new OADeserializer().parseOAtoOAExtended(saved));
        }).when(rights).updateOa(any(ObjectAttributeClazz.class));

        updater.updateRecursive(root.getId());

        for (var id : descendants) {
            var saved = nodes.get(id);
            for (String type : List.of("readProperties", "writeProperties", "shareReadProperties", "shareWriteProperties"))
                assertEquals(List.of("3:5"), AccessValidatorTest.ranges(saved.getDigitsAccess(), "code", type));
        }
    }

    private ObjectAttributeClazz copy(ObjectAttributeClazz source) {
        var result = new ObjectAttributeClazz();
        result.setId(source.getId());
        result.setReadProperties(new HashSet<>(source.getReadProperties()));
        result.setWriteProperties(new HashSet<>(source.getWriteProperties()));
        result.setShareReadProperties(new HashSet<>(source.getShareReadProperties()));
        result.setShareWriteProperties(new HashSet<>(source.getShareWriteProperties()));
        result.setDigitsAccess(new HashSet<>(source.getDigitsAccess()));
        return result;
    }

    private ObjectAttributeClazz node(String... properties) {
        var node = new ObjectAttributeClazz();
        node.setId(UUID.randomUUID());
        node.setReadProperties(new HashSet<>(Set.of(properties)));
        node.setWriteProperties(new HashSet<>(Set.of(properties)));
        node.setShareReadProperties(new HashSet<>(Set.of(properties)));
        node.setShareWriteProperties(new HashSet<>(Set.of(properties)));
        nodes.put(node.getId(), node);
        return node;
    }

    private void connect(ObjectAttributeClazz parent, ObjectAttributeClazz child) {
        children.computeIfAbsent(parent.getId(), id -> new ArrayList<>())
                .add(new RightsConnection(child.getId(), parent.getId()));
    }
}

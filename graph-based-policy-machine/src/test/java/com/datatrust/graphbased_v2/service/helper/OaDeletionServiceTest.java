package com.datatrust.graphbased_v2.service.helper;

import com.datatrust.graphbased_v2.domain.graph.relationships.Assignment;
import com.datatrust.graphbased_v2.domain.graph.relationships.RelationshipConnection;
import com.datatrust.graphbased_v2.domain.graph.relationships.RightsConnection;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import com.datatrust.graphbased_v2.repository.AssigmentRepository;
import com.datatrust.graphbased_v2.repository.ObjectAttributeClazzRepository;
import com.datatrust.graphbased_v2.repository.RelationshipConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OaDeletionServiceTest {

    @Mock private RightsConnectionRepository rightsConnections;
    @Mock private RelationshipConnectionRepository relations;
    @Mock private AssigmentRepository assignments;
    @Mock private RightsRepository rights;
    @Mock private ObjectAttributeClazzRepository objectAttributes;

    private OaDeletionService service;

    @BeforeEach
    void setUp() {
        service = new OaDeletionService(rightsConnections, relations, assignments, rights, objectAttributes);
    }

    @Test
    void deletesCompleteBranchedSubtreeLeavesFirstAndCleansEveryGrant() {
        UUID root = id(1), left = id(2), right = id(3), grandchild = id(4);
        Map<UUID, List<RightsConnection>> graph = new LinkedHashMap<>();
        Map<UUID, RightsConnection> outgoing = new HashMap<>();
        outgoing.put(left, connect(graph, left, root));
        outgoing.put(right, connect(graph, right, root));
        outgoing.put(grandchild, connect(graph, grandchild, left));
        outgoing.put(root, new RightsConnection(root, id(99)));
        useGraph(graph);

        Map<UUID, RelationshipConnection> relationByNode = new HashMap<>();
        Map<UUID, Assignment> assignmentByNode = new HashMap<>();
        for (UUID node : List.of(root, left, right, grandchild)) {
            var relation = new RelationshipConnection(id(100), node, false, true, false);
            var assignment = new Assignment(id(200), node);
            relationByNode.put(node, relation);
            assignmentByNode.put(node, assignment);
            when(relations.getIncomingRelationships(node)).thenReturn(List.of(relation));
            when(assignments.getIncomingRelationships(node)).thenReturn(List.of(assignment));
            when(rightsConnections.getOutgoingRelationships(node)).thenReturn(List.of(outgoing.get(node)));
        }

        service.deleteSubtree(root);

        List<UUID> deleted = deletedNodes(4);
        assertTrue(deleted.indexOf(grandchild) < deleted.indexOf(left));
        assertTrue(deleted.indexOf(left) < deleted.indexOf(root));
        assertTrue(deleted.indexOf(right) < deleted.indexOf(root));
        assertEquals(Set.of(root, left, right, grandchild), Set.copyOf(deleted));

        InOrder cleanup = inOrder(relations, assignments, rightsConnections, rights, objectAttributes);
        for (UUID node : deleted) {
            cleanup.verify(relations).deleteRelationship(relationByNode.get(node));
            cleanup.verify(assignments).deleteRelationship(assignmentByNode.get(node));
            cleanup.verify(rightsConnections).deleteRelationship(outgoing.get(node));
            cleanup.verify(rights).deleteDigitAccess(node);
            cleanup.verify(objectAttributes).deleteById(node);
        }
        verify(rightsConnections, never()).getIncomingRelationships(id(99));
        verify(objectAttributes, never()).deleteById(id(99));
    }

    @Test
    void deletesSharedDescendantsOnceForOverlappingImmutableRootSets() {
        UUID root = id(1), child = id(2), otherRoot = id(3), shared = id(4);
        Map<UUID, List<RightsConnection>> graph = new LinkedHashMap<>();
        connect(graph, child, root);
        connect(graph, shared, child);
        connect(graph, shared, otherRoot);
        useGraph(graph);
        Set<UUID> roots = Set.of(root, child, otherRoot);

        service.deleteSubtrees(roots);

        List<UUID> deleted = deletedNodes(4);
        assertEquals(Set.of(root, child, otherRoot, shared), Set.copyOf(deleted));
        assertTrue(deleted.indexOf(shared) < deleted.indexOf(child));
        assertTrue(deleted.indexOf(shared) < deleted.indexOf(otherRoot));
        assertTrue(deleted.indexOf(child) < deleted.indexOf(root));
        assertEquals(Set.of(root, child, otherRoot), roots);
        for (UUID node : deleted) {
            verify(rightsConnections).getIncomingRelationships(node);
        }
    }

    @Test
    void rejectsCycleBeforeDeletingEvenAnUnrelatedAcyclicRoot() {
        UUID first = id(1), second = id(2), acyclicRoot = id(3);
        Map<UUID, List<RightsConnection>> graph = new LinkedHashMap<>();
        connect(graph, first, second);
        connect(graph, second, first);
        useGraph(graph);

        var error = assertThrows(BadRequestException.class,
                () -> service.deleteSubtrees(Set.of(acyclicRoot, first)));

        assertEquals("Cannot delete cyclic access rights graph.", error.getMessage());
        verifyNoInteractions(relations, assignments, rights, objectAttributes);
        verify(rightsConnections, never()).deleteRelationship(any(RightsConnection.class));
    }

    @Test
    void rejectsSelfLoopBeforeMutatingAnyRepository() {
        UUID root = id(1);
        Map<UUID, List<RightsConnection>> graph = new LinkedHashMap<>();
        connect(graph, root, root);
        useGraph(graph);

        assertThrows(BadRequestException.class, () -> service.deleteSubtree(root));

        verifyNoInteractions(relations, assignments, rights, objectAttributes);
        verify(rightsConnections, never()).deleteRelationship(any(RightsConnection.class));
    }

    @Test
    void snapshotsLiveRelationshipListsBeforeDeletingTheirEntries() {
        UUID root = id(1);
        var liveRelations = new ArrayList<>(List.of(
                new RelationshipConnection(id(10), root, false, true, false),
                new RelationshipConnection(id(11), root, false, false, true)));
        var liveAssignments = new ArrayList<>(List.of(
                new Assignment(id(20), root), new Assignment(id(21), root)));
        var liveRights = new ArrayList<>(List.of(
                new RightsConnection(root, id(30)), new RightsConnection(root, id(31))));
        when(relations.getIncomingRelationships(root)).thenReturn(liveRelations);
        when(assignments.getIncomingRelationships(root)).thenReturn(liveAssignments);
        when(rightsConnections.getOutgoingRelationships(root)).thenReturn(liveRights);
        doAnswer(call -> { liveRelations.removeIf(edge -> edge == call.getArgument(0)); return null; })
                .when(relations).deleteRelationship(any(RelationshipConnection.class));
        doAnswer(call -> { liveAssignments.removeIf(edge -> edge == call.getArgument(0)); return null; })
                .when(assignments).deleteRelationship(any(Assignment.class));
        doAnswer(call -> { liveRights.removeIf(edge -> edge == call.getArgument(0)); return null; })
                .when(rightsConnections).deleteRelationship(any(RightsConnection.class));

        service.deleteSubtree(root);

        assertTrue(liveRelations.isEmpty());
        assertTrue(liveAssignments.isEmpty());
        assertTrue(liveRights.isEmpty());
        verify(rights).deleteDigitAccess(root);
        verify(objectAttributes).deleteById(root);
    }

    @Test
    void handlesLongChainsWithoutRecursion() {
        int length = 4096;
        Map<UUID, List<RightsConnection>> graph = new LinkedHashMap<>();
        for (int index = 1; index < length; index++) {
            connect(graph, id(index + 1), id(index));
        }
        useGraph(graph);

        service.deleteSubtree(id(1));

        List<UUID> deleted = deletedNodes(length);
        assertEquals(id(length), deleted.get(0));
        assertEquals(id(1), deleted.get(length - 1));
        assertEquals(length, Set.copyOf(deleted).size());
    }

    @Test
    void doesNotVisitUnrelatedSubtrees() {
        UUID root = id(1), child = id(2), unrelated = id(3);
        Map<UUID, List<RightsConnection>> graph = new LinkedHashMap<>();
        connect(graph, child, root);
        connect(graph, id(4), unrelated);
        useGraph(graph);

        service.deleteSubtree(root);

        assertEquals(Set.of(root, child), Set.copyOf(deletedNodes(2)));
        verify(rightsConnections, never()).getIncomingRelationships(unrelated);
        verify(objectAttributes, never()).deleteById(unrelated);
        verify(objectAttributes, never()).deleteById(id(4));
    }

    @Test
    void emptyRootSetDoesNothing() {
        service.deleteSubtrees(Set.of());

        verifyNoInteractions(rightsConnections, relations, assignments, rights, objectAttributes);
    }

    private void useGraph(Map<UUID, List<RightsConnection>> graph) {
        when(rightsConnections.getIncomingRelationships(any(UUID.class)))
                .thenAnswer(call -> graph.getOrDefault(call.getArgument(0), List.of()));
    }

    private RightsConnection connect(Map<UUID, List<RightsConnection>> graph, UUID child, UUID parent) {
        var connection = new RightsConnection(child, parent);
        graph.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(connection);
        graph.computeIfAbsent(child, ignored -> new ArrayList<>());
        return connection;
    }

    private List<UUID> deletedNodes(int count) {
        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(objectAttributes, times(count)).deleteById(ids.capture());
        return ids.getAllValues();
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}

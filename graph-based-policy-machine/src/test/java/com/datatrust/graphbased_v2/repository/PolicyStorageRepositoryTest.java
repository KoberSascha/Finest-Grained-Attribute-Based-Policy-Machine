package com.datatrust.graphbased_v2.repository;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PolicyStorageRepositoryTest {
    @Test
    void exposesPersistedIdsFieldSetsDigitRangesAndOnlyEdgesWhoseEndpointsArePresent() {
        var graph = mock(GraphRepository.class);
        var result = mock(Result.class);
        var row = mock(Record.class);
        var grant = node(10, "OA", Map.of("id", "grant", "readProperties", List.of("name", "postcode"),
                "shareReadProperties", List.of("name"), "databasePassword", "must not leave database"));
        var digits = node(11, "DA", Map.of("id", "digits", "property", "postcode", "propertytype", "readProperties",
                "digitstart", 0, "digitstop", 2));
        var recipient = node(12, "I", Map.of("id", "recipient", "name", "private identity metadata", "properties", "secret"));
        var relation = relationship(20, 12, 10, "relation", Map.of("access", 1, "control", 0, "owns", 0, "token", "secret"));
        var digitEdge = relationship(21, 10, 11, "digitAccess", Map.of());
        var outside = relationship(22, 10, 99, "rights", Map.of());
        when(graph.execute(anyString(), anyMap(), eq(false))).thenReturn(result);
        when(result.hasNext()).thenReturn(true);
        when(result.single()).thenReturn(row);
        when(row.get("nodes")).thenReturn(Values.value(List.of(grant, digits, recipient)));
        when(row.get("relationships")).thenReturn(Values.value(List.of(relation, digitEdge, outside)));
        var appId = UUID.randomUUID(); var objectId = UUID.randomUUID();
        var identityId = UUID.randomUUID(); var callerId = UUID.randomUUID();

        var excerpt = new PolicyStorageRepository(graph).read(appId, objectId, identityId, callerId).orElseThrow();

        assertEquals("Neo4j", excerpt.storage());
        assertEquals(3, excerpt.nodes().size());
        var oa = excerpt.nodes().stream().filter(node -> node.id().equals("grant")).findFirst().orElseThrow();
        assertEquals(List.of("name", "postcode"), oa.properties().get("readProperties"));
        assertFalse(oa.properties().containsKey("databasePassword"));
        var identity = excerpt.nodes().stream().filter(node -> node.id().equals("recipient")).findFirst().orElseThrow();
        assertEquals(Map.of("id", "recipient"), identity.properties());
        assertEquals(2, excerpt.edges().size());
        var access = excerpt.edges().stream().filter(edge -> edge.type().equals("relation")).findFirst().orElseThrow();
        assertEquals(Map.of("access", 1L, "control", 0L, "owns", 0L), access.properties());
        verify(graph).execute(PolicyStorageRepository.QUERY, Map.of("application", appId.toString(), "object", objectId.toString(),
                "identity", identityId.toString(), "requestedBy", callerId.toString()), false);
    }

    @Test
    void absentAuthorizedGrantProducesNoExcerpt() {
        var graph = mock(GraphRepository.class); var result = mock(Result.class);
        when(graph.execute(anyString(), anyMap(), eq(false))).thenReturn(result);
        when(result.hasNext()).thenReturn(false);
        assertTrue(new PolicyStorageRepository(graph).read(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void preservesDistinctGrantIdsAndBothRightsEdgesForAnAuthorizedOnwardLineage() {
        var graph = mock(GraphRepository.class); var result = mock(Result.class); var row = mock(Record.class);
        var root = node(1, "OA", Map.of("id", "owner-root", "readProperties", List.of("name", "email", "city")));
        var parent = node(2, "OA", Map.of("id", "partner-grant", "readProperties", List.of("name", "city")));
        var child = node(3, "OA", Map.of("id", "analyst-grant", "readProperties", List.of("city")));
        var owner = node(4, "I", Map.of("id", "owner"));
        var partner = node(5, "I", Map.of("id", "partner"));
        var analyst = node(6, "I", Map.of("id", "analyst"));
        when(graph.execute(anyString(), anyMap(), eq(false))).thenReturn(result);
        when(result.hasNext()).thenReturn(true); when(result.single()).thenReturn(row);
        when(row.get("nodes")).thenReturn(Values.value(List.of(root, parent, child, owner, partner, analyst)));
        when(row.get("relationships")).thenReturn(Values.value(List.of(
                relationship(7, 3, 2, "rights", Map.of()), relationship(8, 2, 1, "rights", Map.of()),
                relationship(9, 5, 3, "relation", Map.of("control", 1, "access", 0)),
                relationship(10, 6, 3, "relation", Map.of("control", 0, "access", 1)))));

        var excerpt = new PolicyStorageRepository(graph).read(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).orElseThrow();

        assertEquals(3, excerpt.nodes().stream().filter(node -> node.labels().contains("OA")).count());
        assertTrue(excerpt.edges().stream().anyMatch(edge -> edge.source().equals("analyst-grant")
                && edge.target().equals("partner-grant") && edge.type().equals("rights")));
        assertTrue(excerpt.edges().stream().anyMatch(edge -> edge.source().equals("partner-grant")
                && edge.target().equals("owner-root") && edge.type().equals("rights")));
        var childProjection = excerpt.nodes().stream().filter(node -> node.id().equals("analyst-grant")).findFirst().orElseThrow();
        assertEquals(List.of("city"), childProjection.properties().get("readProperties"));
        assertFalse(childProjection.properties().get("readProperties").equals(root.get("readProperties").asObject()));
    }

    private static InternalNode node(long id, String label, Map<String, Object> properties) {
        return new InternalNode(id, List.of(label), values(properties));
    }
    private static InternalRelationship relationship(long id, long source, long target, String type, Map<String, Object> properties) {
        return new InternalRelationship(id, source, target, type, values(properties));
    }
    private static Map<String, Value> values(Map<String, Object> properties) {
        Map<String, Value> values = new HashMap<>();
        properties.forEach((key, value) -> values.put(key, Values.value(value)));
        return values;
    }
}

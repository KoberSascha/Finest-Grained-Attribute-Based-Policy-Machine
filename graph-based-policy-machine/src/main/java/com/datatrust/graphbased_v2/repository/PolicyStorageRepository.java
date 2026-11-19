package com.datatrust.graphbased_v2.repository;

import com.datatrust.graphbased_v2.controller.dto.PolicyStorageDTO;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class PolicyStorageRepository {
    private final GraphRepository graph;

    public PolicyStorageRepository(GraphRepository graph) { this.graph = graph; }

    // The selected recipient's grants must descend from an OA this caller can access or
    // control. Only those authorized lineages are returned; sibling grants never enter.
    // Every lineage OA must be assigned to this exact application-scoped object.
    static final String QUERY = "MATCH (a:A {id:$application})-[:application]->(e:E)-[:entity]->(o:O {id:$object}) "
            + "MATCH (identity:I {id:$identity})-[:relation {access:1}]->(grant:OA)<-[:assigned]-(o) "
            + "MATCH (caller:I {id:$requestedBy}) "
            + "MATCH lineage=(grant)-[:rights*0..]->(anchor:OA), (caller)-[authorization:relation]->(anchor) "
            + "WHERE (authorization.access=1 OR authorization.control=1) "
            + "AND all(policyNode IN nodes(lineage) WHERE (o)-[:assigned]->(policyNode)) "
            + "WITH a,e,o,collect(DISTINCT nodes(lineage)) AS lineages "
            + "UNWIND lineages AS lineageNodes UNWIND lineageNodes AS oa "
            + "WITH a,e,o,collect(DISTINCT oa) AS oas "
            + "UNWIND oas AS oa "
            + "MATCH (relatedIdentity:I)-[identityRelation:relation]->(oa) "
            + "WHERE identityRelation.access=1 OR identityRelation.control=1 OR identityRelation.owns=1 "
            + "WITH a,e,o,oas,collect(DISTINCT relatedIdentity) AS identities "
            + "UNWIND oas AS oa "
            + "OPTIONAL MATCH (oa)-[:digitAccess]->(da:DA) "
            + "WITH a,e,o,identities,oas,collect(DISTINCT da) AS das "
            + "UNWIND [a,e,o]+identities+oas+das AS node "
            + "WITH collect(DISTINCT node) AS nodes "
            + "UNWIND nodes AS source "
            + "OPTIONAL MATCH (source)-[relationship]->(target) "
            + "WHERE target IN nodes AND type(relationship) IN ['application','entity','assigned','relation','rights','digitAccess'] "
            + "RETURN nodes,collect(DISTINCT relationship) AS relationships";

    public Optional<PolicyStorageDTO> read(UUID applicationId, UUID objectId, UUID identityId, UUID requestedById) {
        var result = graph.execute(QUERY, Map.of("application", applicationId.toString(), "object", objectId.toString(),
                "identity", identityId.toString(), "requestedBy", requestedById.toString()), false);
        if (!result.hasNext()) return Optional.empty();
        var row = result.single();
        var storedNodes = row.get("nodes").asList(value -> value.asNode());
        if (storedNodes.stream().noneMatch(node -> node.hasLabel("OA"))) return Optional.empty();

        // Internal database IDs are used only to join actual edges to persisted UUIDs.
        Map<Long, String> identifiers = new HashMap<>();
        var nodes = new ArrayList<PolicyStorageDTO.Node>();
        for (var node : storedNodes) {
            var projected = projectNode(node);
            identifiers.put(node.id(), projected.id());
            nodes.add(projected);
        }
        var edges = new ArrayList<PolicyStorageDTO.Edge>();
        for (var value : row.get("relationships").values()) {
            if (value.isNull()) continue;
            var relationship = value.asRelationship();
            String source = identifiers.get(relationship.startNodeId()), target = identifiers.get(relationship.endNodeId());
            if (source != null && target != null)
                edges.add(new PolicyStorageDTO.Edge(source, target, relationship.type(), projectRelationship(relationship)));
        }
        nodes.sort(Comparator.comparing(PolicyStorageDTO.Node::id));
        edges.sort(Comparator.comparing(PolicyStorageDTO.Edge::source).thenComparing(PolicyStorageDTO.Edge::type)
                .thenComparing(PolicyStorageDTO.Edge::target));
        return Optional.of(new PolicyStorageDTO(applicationId, objectId, identityId, requestedById,
                "Neo4j", Instant.now(), nodes, edges));
    }

    static PolicyStorageDTO.Node projectNode(Node node) {
        List<String> labels = new ArrayList<>();
        node.labels().forEach(label -> { if (Set.of("A", "E", "O", "I", "OA", "DA").contains(label)) labels.add(label); });
        Collections.sort(labels);
        Set<String> fields = new LinkedHashSet<>(List.of("id", "type"));
        if (!node.hasLabel("I")) fields.addAll(List.of("name", "entityClass"));
        if (node.hasLabel("OA")) fields.addAll(List.of("readProperties", "writeProperties", "shareReadProperties", "shareWriteProperties"));
        if (node.hasLabel("DA")) fields.addAll(List.of("property", "propertytype", "digitstart", "digitstop"));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (var field : fields)
            if (node.containsKey(field) && !node.get(field).isNull()) properties.put(field, node.get(field).asObject());
        return new PolicyStorageDTO.Node(node.get("id").asString(), labels, properties);
    }

    static Map<String, Object> projectRelationship(Relationship relationship) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (relationship.type().equals("relation")) {
            for (var field : List.of("access", "owns", "control"))
                if (relationship.containsKey(field) && !relationship.get(field).isNull())
                    properties.put(field, relationship.get(field).asObject());
        }
        return properties;
    }
}

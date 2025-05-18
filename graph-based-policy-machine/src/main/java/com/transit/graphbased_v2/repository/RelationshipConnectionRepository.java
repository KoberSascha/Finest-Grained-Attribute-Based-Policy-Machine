package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.common.ConnectionType;
import com.transit.graphbased_v2.common.RightsConstants;
import com.transit.graphbased_v2.domain.graph.relationships.RelationshipConnection;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RelationshipConnectionRepository extends RelationshipClazzRepository<RelationshipConnection> {

    @LogExecutionTime
    public RelationshipConnection createRelationship(RelationshipConnection relationship) {
        return super.createRelationship(relationship, ConnectionType.RELATION);

    }


    public void deleteRelationship(RelationshipConnection relationship) {

        super.deleteRelationship(relationship, ConnectionType.RELATION);
    }

    public Optional<RelationshipConnection> getRelationship(UUID sourceId, UUID targetId) {

        return super.getRelationship(sourceId, targetId, ConnectionType.RELATION);
    }

    public List<RelationshipConnection> getIncomingRelationships(UUID targetId) {
        return super.getIncomingRelationships(targetId, ConnectionType.RELATION);
    }

    public List<RelationshipConnection> getOutgoingRelationships(UUID sourceId) {
        return super.getOutgoingRelationships(sourceId, ConnectionType.RELATION);
    }

    private Set<String> parseOps(Value values) {
        Set<String> ops = new HashSet<>();
        for (Value value : values.values()) {
            ops.add(value.asString().replaceAll("[\\[\\]\"]", ""));
        }
        ops.remove(RightsConstants.EMPTY_OPERATIONS);
        return ops;
    }


    @Override
    public Optional<RelationshipConnection> getRel(Record result) {

        String accessStr = Integer.toString(result.get(2).asInt());
        String controlStr = Integer.toString(result.get(3).asInt());
        String ownsStr = Integer.toString(result.get(4).asInt());

        return Optional.of(new RelationshipConnection(
                UUID.fromString(result.get(0).asString()),
                UUID.fromString(result.get(1).asString()),
                rewriteBoolean.getBooleanString(accessStr),
                rewriteBoolean.getBooleanString(controlStr),
                rewriteBoolean.getBooleanString(ownsStr)
        ));
    }


}

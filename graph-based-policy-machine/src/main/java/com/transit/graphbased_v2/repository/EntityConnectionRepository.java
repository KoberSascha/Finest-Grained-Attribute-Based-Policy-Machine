package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.common.ConnectionType;
import com.transit.graphbased_v2.common.RightsConstants;
import com.transit.graphbased_v2.domain.graph.relationships.EntityConnection;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EntityConnectionRepository extends RelationshipClazzRepository<EntityConnection> {
	@LogExecutionTime
	public EntityConnection createRelationship(EntityConnection relationship) {
		return super.createRelationship(relationship, ConnectionType.ENTITY);
		
	}
	
	
	public void deleteRelationship(EntityConnection relationship) {
		
		super.deleteRelationship(relationship, ConnectionType.ENTITY);
	}
	
	public Optional<EntityConnection> getRelationship(UUID sourceId, UUID targetId) {
		
		return super.getRelationship(sourceId, targetId, ConnectionType.ENTITY);
	}
	
	public List<EntityConnection> getIncomingRelationships(UUID targetId) {
		return super.getIncomingRelationships(targetId, ConnectionType.ENTITY);
	}
	
	public List<EntityConnection> getOutgoingRelationships(UUID sourceId) {
		return super.getOutgoingRelationships(sourceId, ConnectionType.ENTITY);
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
	public Optional<EntityConnection> getRel(Record result) {
		return Optional.of(new EntityConnection(UUID.fromString(result.get(0).asString()), UUID.fromString(result.get(1).asString())));
	}
	
	
}

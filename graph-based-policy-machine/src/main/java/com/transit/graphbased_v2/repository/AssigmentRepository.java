package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.common.ConnectionType;
import com.transit.graphbased_v2.domain.graph.relationships.Assignment;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AssigmentRepository extends RelationshipClazzRepository<Assignment> {
	
	@LogExecutionTime
	public Assignment createRelationship(Assignment relationship) {
		return super.createRelationship(relationship, ConnectionType.ASSIGNMENT);
		
	}
	
	
	public void deleteRelationship(Assignment relationship) {
		
		super.deleteRelationship(relationship, ConnectionType.ASSIGNMENT);
	}
	
	public Optional<Assignment> getRelationship(UUID sourceId, UUID targetId) {
		
		return super.getRelationship(sourceId, targetId, ConnectionType.ASSIGNMENT);
	}
	
	public List<Assignment> getIncomingRelationships(UUID targetId) {
		return super.getIncomingRelationships(targetId, ConnectionType.ASSIGNMENT);
	}
	
	public List<Assignment> getOutgoingRelationships(UUID sourceId) {
		return super.getOutgoingRelationships(sourceId, ConnectionType.ASSIGNMENT);
	}
	
	
	@Override
	public Optional<Assignment> getRel(Record result) {
		return Optional.of(new Assignment(UUID.fromString(result.get(0).asString()), UUID.fromString(result.get(1).asString())));
	}
	
}

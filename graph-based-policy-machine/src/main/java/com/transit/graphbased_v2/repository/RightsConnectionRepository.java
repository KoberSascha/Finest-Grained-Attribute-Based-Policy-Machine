package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.common.ConnectionType;
import com.transit.graphbased_v2.domain.graph.relationships.RightsConnection;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RightsConnectionRepository extends RelationshipClazzRepository<RightsConnection> {
	@LogExecutionTime
	public RightsConnection createRelationship(RightsConnection relationship) {
		return super.createRelationship(relationship, ConnectionType.RIGHTS);
		
	}
	
	
	public void deleteRelationship(RightsConnection relationship) {
		super.deleteRelationship(relationship, ConnectionType.RIGHTS);
	}
	
	public Optional<RightsConnection> getRelationship(UUID sourceId, UUID targetId) {
		
		return super.getRelationship(sourceId, targetId, ConnectionType.RIGHTS);
	}
	
	
	public List<RightsConnection> getIncomingRelationships(UUID targetId) {
		return super.getIncomingRelationships(targetId, ConnectionType.RIGHTS);
	}
	
	public List<RightsConnection> getOutgoingRelationships(UUID sourceId) {
		return super.getOutgoingRelationships(sourceId, ConnectionType.RIGHTS);
	}
	
	@Override
	public Optional<RightsConnection> getRel(Record result) {
		return Optional.of(new RightsConnection(UUID.fromString(result.get(0).asString()), UUID.fromString(result.get(1).asString())));
	}
	
	
}

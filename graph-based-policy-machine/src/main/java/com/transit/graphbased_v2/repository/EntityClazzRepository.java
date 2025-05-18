package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.domain.graph.nodes.EntityClazz;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository

public interface EntityClazzRepository extends Neo4jRepository<EntityClazz, UUID> {
	
	
	List<EntityClazz> findAllByEntityClass(String entityClass);
	
	@LogExecutionTime
	boolean existsEntityClazzByEntityClass(String entityClass);
}

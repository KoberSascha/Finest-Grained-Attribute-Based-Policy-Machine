package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.domain.graph.nodes.ObjectClazz;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObjectClazzRepository extends Neo4jRepository<ObjectClazz, UUID> {
	@LogExecutionTime
	Optional<ObjectClazz> findById(UUID id);
	
	@LogExecutionTime

//	@Query("MATCH (o:O {id: $id })\n" +
//			"WITH count(*) as count\n" +
//			"CALL apoc.when (count > 0, \n" +
//			"\"RETURN true AS bool\",     \n" +
//			"\"RETURN false AS bool\",    \n" +
//			"{count:count}\n" +
//			") YIELD value\n" +
//			"return value.bool")
		//boolean existsById(@Param("id" UUID id);
	boolean existsById(UUID id);
}

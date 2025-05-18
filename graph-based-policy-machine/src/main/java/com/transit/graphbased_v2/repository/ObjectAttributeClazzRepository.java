package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ObjectAttributeClazzRepository extends Neo4jRepository<ObjectAttributeClazz, UUID> {

}

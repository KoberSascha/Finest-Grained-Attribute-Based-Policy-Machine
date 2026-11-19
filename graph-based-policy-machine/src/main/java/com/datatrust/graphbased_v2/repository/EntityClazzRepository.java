package com.datatrust.graphbased_v2.repository;

import com.datatrust.graphbased_v2.domain.graph.nodes.EntityClazz;
import com.datatrust.graphbased_v2.performacelogging.LogExecutionTime;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository

public interface EntityClazzRepository extends Neo4jRepository<EntityClazz, UUID> {


    List<EntityClazz> findAllByEntityClass(String entityClass);

    @Query("MATCH (:A {id: $applicationId})-[:application]->(e:E {entityClass: $entityClass}) RETURN e LIMIT 1")
    Optional<EntityClazz> findByApplicationIdAndEntityClass(@Param("applicationId") UUID applicationId,
                                                         @Param("entityClass") String entityClass);

    @LogExecutionTime
    boolean existsEntityClazzByEntityClass(String entityClass);
}

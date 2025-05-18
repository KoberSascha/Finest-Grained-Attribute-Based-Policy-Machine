package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.domain.graph.nodes.ApplicationClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectClazz;
import com.transit.graphbased_v2.service.helper.AccessValidator;
import com.transit.graphbased_v2.service.helper.ParseRightsResult;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class HelpersRepository {


    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private ParseRightsResult parseResult;
    @Autowired
    private AccessValidator accessValidator;

    public HelpersRepository() {
    }

    public Optional<ApplicationClazz> getApplicationByIdentityAndName(UUID identityId, String name) {

        StringBuilder builder = new StringBuilder();

        String query = String.format(
                "MATCH (a:A {identityId:'%s', name:'%s'}) ",
                identityId.toString(),
                name
        );

        // Append the constructed query
        builder.append(query);

        builder.append("RETURN a");


        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }

        var r = parseResult.parseApplicationNode(result);

        return Optional.of(r);
    }


    public Optional<List<ApplicationClazz>> getApplications() {
        String query = String.format(
                "MATCH (a:A ) RETURN a"
        );

        Result result = graphRepository.execute(query, false);
        if (!result.hasNext()) {
            return Optional.empty();
        }

        List<ApplicationClazz> applications = parseResult.parseApplicationNodes(result);
        return Optional.of(applications);
    }


    public Optional<List<ApplicationClazz>> getApplicationsByIdentity(UUID identityId) {
        String query = String.format(
                "MATCH (a:A {identityId:'%s'}) RETURN a",
                identityId
        );

        Result result = graphRepository.execute(query, false);
        if (!result.hasNext()) {
            return Optional.empty();
        }

        List<ApplicationClazz> applications = parseResult.parseApplicationNodes(result);
        return Optional.of(applications);
    }


    public Optional<ApplicationClazz> getApplicationByOANode(ObjectAttributeExtendedClazz oaNode) {
        UUID oaId = oaNode.getId();
        String name = oaNode.getName();

        if (oaId == null) {
            return Optional.empty();
        }

        String query = String.format(
                "MATCH (a:A)-[:application]->(e:E)-[:entity]->(o:O)-[:assigned]->(oa:OA {id: '%s', name: '%s'}) " +
                        "RETURN a",
                oaId.toString(), name
        );

        Result result = graphRepository.execute(query, false);
        if (!result.hasNext()) {
            return Optional.empty();
        }

        var r = parseResult.parseApplicationNode(result);

        return Optional.of(r);
    }


    public Optional<ObjectClazz> findApplicationObjectById(UUID applicationId, UUID objectId) {

        String query = String.format(
                "MATCH (a:A {id: '%s'})-[:application]->(e:E)-[:entity]->(o:O {id: '%s'}) " +
                        "RETURN o",
                applicationId, objectId
        );

        Result result = graphRepository.execute(query, false);
        if (!result.hasNext()) {
            return Optional.empty();
        }

        var r = parseResult.parseObjectNodeFromResult(result);

        return Optional.of(r);
    }


}

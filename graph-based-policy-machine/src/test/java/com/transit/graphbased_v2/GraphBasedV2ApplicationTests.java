package com.transit.graphbased_v2;

import com.transit.graphbased_v2.controller.dto.IdentityDTO;
import com.transit.graphbased_v2.controller.dto.OAPropertiesDTO;
import com.transit.graphbased_v2.controller.dto.ObjectDTO;
import com.transit.graphbased_v2.repository.helper.DBClean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.DatabaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GraphBasedV2ApplicationTests {


    private TestRestTemplate restTemplate;


    @Autowired
    private DBClean dbClean;


    @LocalServerPort
    private int port;

    @Autowired
    public GraphBasedV2ApplicationTests(TestRestTemplate testRestTemplate) {
        this.restTemplate = testRestTemplate;
        this.restTemplate.getRestTemplate().setInterceptors(Collections.singletonList((request, body, execution) -> {
            request.getHeaders().add("X-API-KEY", "614D5358726EC07655BF4A38CA751A74C80F42CE571E5055FEF920ECCAC2624C");
            return execution.execute(request, body);
        }));
    }

    @Test
    void contextLoads() {
    }


    @Test
    void testCreateEntityNodeMultipleTimes() throws DatabaseException {

        //dbClean.clear();
        int amountOfLoops = 1000;
        for (int a = 0; a < amountOfLoops; a++) {
            Set<String> props = new HashSet<>(Arrays.asList("a", "b", "c", "d", "e"));
            Set<String> props2 = new HashSet<>(Arrays.asList("a", "b", "c", "d"));
            var start = System.currentTimeMillis();

            UUID group1Id = UUID.randomUUID();
            createGroupNode(group1Id, "Group#" + group1Id);
            UUID group2Id = UUID.randomUUID();
            createGroupNode(group2Id, "Group#" + group2Id);

            int amountOfTestData = 1000;

            for (int i = 0; i < amountOfTestData; i++) {

                UUID oId = UUID.randomUUID();
                createObjectNode(oId, group1Id, props);
                createConnection(oId, group1Id, group2Id, props2);

            }
            var stop = System.currentTimeMillis();
            log.error(DurationFormatUtils.formatDurationWords(stop - start, true, true));
            // clearDatabase();
        }
    }

    private void createGroupNode(UUID nodeId, String name) {
        IdentityDTO nodeDTO = new IdentityDTO();
        nodeDTO.setId(nodeId);
        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/identity", nodeDTO, Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private void createObjectNode(UUID oId, UUID identityId, Set<String> properties) {

        // Create object for requestbody
        ObjectDTO creationFullyDTO = new ObjectDTO();
        creationFullyDTO.setObjectId(oId);
        creationFullyDTO.setIdentityId(identityId);
        creationFullyDTO.setObjectEntityClass("GroupObject");
        //creationFullyDTO.setProperties(new OAPropertiesDTO(properties, properties, properties, properties));
        creationFullyDTO.setProperties(properties);


        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/object", creationFullyDTO, Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private void createConnection(UUID oId, UUID uaIdCalling, UUID uaIdTarget, Set<String> props) {


        var dto = new OAPropertiesDTO(props, props, props, props);
        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/access/" + oId + "?identityId=" + uaIdTarget + "&requestedById=" + uaIdCalling, dto, Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());


    }

    @Test
    void testCreateEntityNodeMultipleTimesParallel() throws DatabaseException {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        //dbClean.clear();
        Set<String> props = new HashSet<>(Arrays.asList("a", "b", "c", "d", "e"));
        Set<String> props2 = new HashSet<>(Arrays.asList("a", "b", "c", "d"));
        log.error("Ready with clear");
        var start = System.currentTimeMillis();
     
        UUID group1Id = UUID.randomUUID();
        createGroupNode(group1Id, "Group#" + group1Id);
        
        UUID group2Id = UUID.randomUUID();
        createGroupNode(group2Id, "Group#" + group2Id);

        //int amountOfTestData = 1;
        int amountOfTestData = 10000000;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        

        for (int i = 0; i < amountOfTestData; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                UUID oId = UUID.randomUUID();
                createObjectNode(oId, group1Id, props);
                createConnection(oId, group1Id, group2Id, props2);

            }, pool);
            futures.add(future);
            if (i % 100000 == 0 && i != 0) {
                var startTemp = System.currentTimeMillis();
                CompletableFuture<Void> allCompleted = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
                try {
                    allCompleted.get();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                futures.clear();
                var stoptemp = System.currentTimeMillis();
                log.error("Time for " + (i - 1000) + " to i " + i + " : " + DurationFormatUtils.formatDurationWords(stoptemp - startTemp, true, true) + "\n");
            }
        }
        log.error("Before starting futures");
        CompletableFuture<Void> allCompleted = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allCompleted.get();
            log.error("Main part is ready");
            futures.forEach(entry -> {
                try {
                    entry.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        var stop = System.currentTimeMillis();
        log.error("Time for all : " + DurationFormatUtils.formatDurationWords(stop - start, true, true));
    }
}

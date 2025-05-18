package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.config.StartConfiguration;
import com.transit.graphbased_v2.service.helper.ParseRightsResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
@Slf4j
public class GraphRepository {

    private static String uri;

    private static String username;
    private static String password;

    private Driver driver;

    @Autowired
    private ParseRightsResult parseResult;

    public GraphRepository() {
        initialize();
    }

    public void initialize() {
        if (!StartConfiguration.getProperty("spring.neo4j.uri").equals("")) {
            uri = StartConfiguration.getProperty("spring.neo4j.uri");
            username = StartConfiguration.getProperty("spring.neo4j.authentication.username");
            password = StartConfiguration.getProperty("spring.neo4j.authentication.password");
            //connect to database
            connect();
        }
    }

    public void connect() {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        System.out.println("Connected to Neo4j");
    }

    public Result execute(String cypher, boolean commit) {
        if (driver == null) {
            initialize();
        }
        Result result;
        final StopWatch stopWatch = new StopWatch();
        // cypher = "CYPHER runtime=parallel " + cypher;
        stopWatch.start();
        var session = driver.session();
        if (!commit) {
            result = session.run(cypher);
        } else {
            var transaction = session.beginTransaction();
            result = transaction.run(cypher);
            transaction.commit();
        }
        stopWatch.stop();
        log.error("\"{}\" executed in {}", "Time to execute Cypher Query", DurationFormatUtils.formatDurationHMS(stopWatch.getTotalTimeMillis()));
        return result;
    }

}

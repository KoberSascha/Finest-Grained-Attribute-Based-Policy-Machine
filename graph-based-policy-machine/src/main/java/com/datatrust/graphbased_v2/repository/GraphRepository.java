package com.datatrust.graphbased_v2.repository;

import com.datatrust.graphbased_v2.config.StartConfiguration;
import com.datatrust.graphbased_v2.service.helper.ParseRightsResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

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

    GraphRepository(Driver driver) {
        this.driver = driver;
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
        return execute(cypher, Map.of(), commit);
    }

    public Result execute(String cypher, Map<String, Object> parameters, boolean commit) {
        if (driver == null) {
            initialize();
        }
        final StopWatch stopWatch = new StopWatch();
        // cypher = "CYPHER runtime=parallel " + cypher;
        stopWatch.start();
        Result result;
        try (var session = driver.session()) {
            if (!commit) {
                result = BufferedResult.read(session.run(cypher, parameters));
            } else {
                try (var transaction = session.beginTransaction()) {
                    result = BufferedResult.read(transaction.run(cypher, parameters));
                    transaction.commit();
                }
            }
        }
        stopWatch.stop();
        log.error("\"{}\" executed in {}", "Time to execute Cypher Query", DurationFormatUtils.formatDurationHMS(stopWatch.getTotalTimeMillis()));
        return result;
    }

    /** Detaches records and completes auto-commit writes before closing the session. */
    private static final class BufferedResult implements Result {
        private final List<String> keys;
        private final List<Record> records;
        private final ResultSummary summary;
        private int cursor;

        private BufferedResult(List<String> keys, List<Record> records, ResultSummary summary) {
            this.keys = List.copyOf(keys);
            this.records = new ArrayList<>(records);
            this.summary = summary;
        }

        static BufferedResult read(Result source) {
            List<String> keys = source.keys();
            List<Record> records = source.list();
            return new BufferedResult(keys, records, source.consume());
        }

        @Override public List<String> keys() { return keys; }
        @Override public boolean hasNext() { return cursor < records.size(); }
        @Override public Record next() {
            if (!hasNext()) throw new NoSuchRecordException("No more records.");
            return records.get(cursor++);
        }
        @Override public Record peek() {
            if (!hasNext()) throw new NoSuchRecordException("No more records.");
            return records.get(cursor);
        }
        @Override public Record single() {
            if (records.size() - cursor != 1) throw new NoSuchRecordException("Expected exactly one record.");
            return next();
        }
        @Override public Stream<Record> stream() { return list().stream(); }
        @Override public List<Record> list() {
            List<Record> result = new ArrayList<>(records.subList(cursor, records.size()));
            cursor = records.size();
            return result;
        }
        @Override public <T> List<T> list(Function<Record, T> mapper) { return list().stream().map(mapper).toList(); }
        @Override public ResultSummary consume() { cursor = records.size(); return summary; }
    }

}

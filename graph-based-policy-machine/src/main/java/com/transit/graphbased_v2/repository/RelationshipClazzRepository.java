package com.transit.graphbased_v2.repository;

import com.transit.graphbased_v2.common.ConnectionType;
import com.transit.graphbased_v2.common.Constants;
import com.transit.graphbased_v2.config.StartConfiguration;
import com.transit.graphbased_v2.domain.graph.relationships.Relationship;
import com.transit.graphbased_v2.domain.graph.relationships.RelationshipConnection;
import com.transit.graphbased_v2.repository.helper.RewriteBoolean;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public abstract class RelationshipClazzRepository<rel extends Relationship> {


    private static String uri;

    private static String username;
    private static String password;
    @Autowired
    RewriteBoolean rewriteBoolean;
    private Driver driver;

    public RelationshipClazzRepository() {
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


    public rel createRelationship(rel relationship, ConnectionType connectionType) {
        String cypher = "";

        if (ConnectionType.ASSIGNMENT.equals(connectionType)) {
            cypher = "MATCH (a:O {id:'" + relationship.getSourceID() + "'}), (b:OA {id:'" + relationship.getTargetID() + "'}) " + "CREATE (a)-[:assigned]->(b)";
            execute(cypher, true);
        } else if (ConnectionType.RIGHTS.equals(connectionType)) {
            cypher = "MATCH (a:OA {id:'" + relationship.getSourceID() + "'}), (b:OA {id:'" + relationship.getTargetID() + "'}) " + "CREATE (a)-[:rights]->(b)";
            execute(cypher, true);
        } else if (ConnectionType.ENTITY.equals(connectionType)) {
            cypher = "MATCH (a:E {id:'" + relationship.getSourceID() + "'}), (b:O {id:'" + relationship.getTargetID() + "'}) " + "CREATE (a)-[:entity]->(b)";
            execute(cypher, true);
        } else if (ConnectionType.RELATION.equals(connectionType)) {
            var relation = (RelationshipConnection) relationship;
            cypher = "MATCH (a:I {id:'" + relationship.getSourceID() + "'}), (b:OA {id:'" + relationship.getTargetID() + "'}) " + "CREATE (a)-[:relation {owns:'" + rewriteBoolean.getStoreString(relation.isOwns()) + "',access:'" + rewriteBoolean.getStoreString(relation.isAccess()) + "',control:'" + rewriteBoolean.getStoreString(relation.isControl()) + "'}]->(b)";
            execute(cypher, true);
        }

        return getRelationship(relationship.getSourceID(), relationship.getTargetID(), connectionType).get();
    }

    public Optional<rel> getRelationship(UUID sourceId, UUID targetID, ConnectionType connectionType) {
        String cypher = "";
        if (ConnectionType.RIGHTS.equals(connectionType)) {
            cypher = "MATCH (n:OA {id:'" + sourceId.toString() + "'})-[r:rights]->(m:OA {id:'" + targetID.toString() + "'}) RETURN n.id, m.id";
        } else if (ConnectionType.ASSIGNMENT.equals(connectionType)) {
            cypher = "MATCH (n:O {id:'" + sourceId.toString() + "'})-[r:assigned]->(m:OA {id:'" + targetID.toString() + "'}) RETURN n.id, m.id";
        } else if (ConnectionType.ENTITY.equals(connectionType)) {
            cypher = "MATCH (n:E {id:'" + sourceId.toString() + "'})-[e:entity]->(m:O {id:'" + targetID.toString() + "'}) RETURN n.id,m.id;";
        } else if (ConnectionType.RELATION.equals(connectionType)) {
            cypher = "MATCH (i:I {id:'" + sourceId.toString() + "'})-[rel:relation]->(oa:OA {id:'" + targetID.toString() + "'}) RETURN i.id,oa.id, rel.owns,rel.access, rel.control;";
        }
        if (cypher.isBlank()) {
            return Optional.empty();
        }
        Result rs = execute(cypher, false);
        if (!rs.hasNext()) {
            return Optional.empty();

        } else {
            return getRel(rs.next());
        }


    }

    public void deleteRelationship(rel relationship, ConnectionType connectionType) {
        String cypher = "MATCH (a {id:'" + relationship.getSourceID() + "'})-[r:";
        if (ConnectionType.ASSIGNMENT.equals(connectionType)) {
            cypher += "assigned";
        } else if (ConnectionType.RIGHTS.equals(connectionType)) {
            cypher += "rights";
        } else if (ConnectionType.ENTITY.equals(connectionType)) {
            cypher += "entity";
        } else if (ConnectionType.RELATION.equals(connectionType)) {
            cypher += "relation";
        }
        cypher += "]->(b{id:'" + relationship.getTargetID() + "'}) DELETE r";
        execute(cypher, true);

    }


    public List<rel> getIncomingRelationships(UUID targetId, ConnectionType connectionType) {
        String cypher = "";
        if (ConnectionType.RIGHTS.equals(connectionType)) {
            cypher = "MATCH (n:OA)-[r:rights]->(m:OA{id:'" + targetId.toString() + "'}) RETURN n.id, m.id";
        } else if (ConnectionType.ASSIGNMENT.equals(connectionType)) {
            cypher = "MATCH (n:O)-[r:assigned]->(m:OA{id:'" + targetId.toString() + "'}) RETURN n.id, m.id";
        } else if (ConnectionType.ENTITY.equals(connectionType)) {
            cypher = "MATCH (n:E)-[e:entity]->(m:O {id:'" + targetId.toString() + "'}) RETURN n.id,m.id;";
        } else if (ConnectionType.RELATION.equals(connectionType)) {
            cypher = "MATCH (i:I)-[rel:relation]->(oa:OA {id:'" + targetId.toString() + "'}) RETURN i.id,oa.id, rel.owns,rel.access, rel.control;";
        }
        if (cypher.isBlank()) {
            return new ArrayList<>();
        }
        Result rs = execute(cypher, false);
        if (!rs.hasNext()) {
            return new ArrayList<>();
        } else {
            var result = new ArrayList<rel>();
            while (rs.hasNext()) {
                result.add(getRel(rs.next()).get());
            }
            return result;
        }


    }

    public abstract Optional<rel> getRel(Record result);

    public List<rel> getOutgoingRelationships(UUID sourceId, ConnectionType connectionType) {
        String cypher = "";
        if (ConnectionType.RIGHTS.equals(connectionType)) {
            cypher = "MATCH (n:OA {id:'" + sourceId.toString() + "'})-[r:rights]->(m:OA) RETURN n.id, m.id";
        } else if (ConnectionType.ASSIGNMENT.equals(connectionType)) {
            cypher = "MATCH (n:O {id:'" + sourceId.toString() + "'})-[r:assigned]->(m:OA) RETURN n.id, m.id";
        } else if (ConnectionType.ENTITY.equals(connectionType)) {
            cypher = "MATCH (n:E {id:'" + sourceId.toString() + "'})-[e:entity]->(m:O ) RETURN n.id,m.id;";
        } else if (ConnectionType.RELATION.equals(connectionType)) {
            cypher = "MATCH (i:I {id:'" + sourceId.toString() + "'})-[rel:relation]->(oa:OA ) RETURN i.id,oa.id, rel.owns,rel.access, rel.control;";
        }
        if (cypher.isBlank()) {
            return new ArrayList<>();
        }
        Result rs = execute(cypher, false);
        if (!rs.hasNext()) {
            return new ArrayList<>();
        } else {
            var result = new ArrayList<rel>();
            while (rs.hasNext()) {
                result.add(getRel(rs.next()).get());
            }
            return result;
        }
    }

    private String setToCypherArray(Set<String> list) {
        String str = "[";
        for (String op : list) {
            op = "'" + op + "'";
            if (str.length() == 1) {
                str += op;
            } else {
                str += Constants.DELIMITER + op;
            }
        }
        str += "]";
        return str;
    }


    public Result execute(String cypher, boolean commit) {
        if (driver == null) {
            initialize();
        }
        var session = driver.session();
        if (!commit) {
            return session.run(cypher);
        } else {
            var transaction = session.beginTransaction();
            var result = transaction.run(cypher);
            transaction.commit();
            return result;
        }
    }

}

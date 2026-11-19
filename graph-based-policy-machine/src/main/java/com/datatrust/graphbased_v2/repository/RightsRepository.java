package com.datatrust.graphbased_v2.repository;

import com.datatrust.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.datatrust.graphbased_v2.controller.dto.IdentityObjectRightsDTO;
import com.datatrust.graphbased_v2.domain.graph.nodes.*;
import com.datatrust.graphbased_v2.service.helper.AccessValidator;
import com.datatrust.graphbased_v2.service.helper.ParseRightsResult;
import com.datatrust.graphbased_v2.service.helper.OADeserializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RightsRepository {


    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private ParseRightsResult parseResult;
    @Autowired
    private AccessValidator accessValidator;

    public RightsRepository() {
    }

    public record ControlledGrant(UUID id, Set<UUID> parentIds) { }
    public record GrantContext(boolean objectExists, boolean giverExists, boolean recipientExists,
                               Set<UUID> sourceIds, List<ControlledGrant> controlledGrants) { }

    public Optional<UUID> findOwnedOaId(UUID applicationId, UUID objectId, UUID identityId) {
        var result = graphRepository.execute("MATCH (:A {id:$application})-[:application]->(:E)-[:entity]->(o:O {id:$object}) "
                + "-[:assigned]->(oa:OA)<-[:relation {owns:1,access:1}]-(:I {id:$identity}) RETURN DISTINCT oa.id AS id",
                Map.of("application", applicationId.toString(), "object", objectId.toString(), "identity", identityId.toString()), false);
        return result.hasNext() ? Optional.of(UUID.fromString(result.next().get("id").asString())) : Optional.empty();
    }

    /** Self-revocation relinquishes received grants, while the ownership root stays intact. */
    public Set<UUID> findReceivedGrantIds(UUID applicationId, UUID objectId, UUID identityId) {
        var result = graphRepository.execute("MATCH (:A {id:$application})-[:application]->(:E)-[:entity]->(o:O {id:$object}) "
                + "-[:assigned]->(oa:OA)<-[r:relation {access:1}]-(:I {id:$identity}) "
                + "WHERE coalesce(r.owns,0) <> 1 RETURN DISTINCT oa.id AS id",
                Map.of("application", applicationId.toString(), "object", objectId.toString(), "identity", identityId.toString()), false);
        return result.list(row -> UUID.fromString(row.get("id").asString())).stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Resolve the logical giver/recipient grant independently of optional-match row order. */
    public GrantContext getGrantContext(UUID applicationId, UUID objectId, UUID giverId, UUID recipientId) {
        String query = "OPTIONAL MATCH (giver:I {id:$giver}) "
                + "OPTIONAL MATCH (recipient:I {id:$recipient}) "
                + "OPTIONAL MATCH (:A {id:$application})-[:application]->(:E)-[:entity]->(o:O {id:$object}) "
                + "WITH giver,recipient,o "
                + "OPTIONAL MATCH (giver)-[:relation {access:1}]->(source:OA)<-[:assigned]-(o) "
                + "WITH giver,recipient,o,collect(DISTINCT source.id) AS sources "
                + "OPTIONAL MATCH (giver)-[:relation {control:1}]->(grant:OA)<-[:relation {access:1}]-(recipient), "
                + "(o)-[:assigned]->(grant) "
                + "RETURN giver.id AS giver,recipient.id AS recipient,o.id AS object,sources,collect(DISTINCT grant.id) AS grants";
        var result = graphRepository.execute(query, Map.of("application", applicationId.toString(), "object", objectId.toString(),
                "giver", giverId.toString(), "recipient", recipientId.toString()), false);
        var row = result.single();
        Set<UUID> sources = new LinkedHashSet<>(row.get("sources").asList(value -> UUID.fromString(value.asString())));
        List<ControlledGrant> grants = new ArrayList<>();
        for (var value : row.get("grants").values()) {
            var id = UUID.fromString(value.asString());
            var parents = graphRepository.execute("MATCH (grant:OA {id:$id}) OPTIONAL MATCH (grant)-[:rights]->(parent:OA) "
                    + "RETURN collect(DISTINCT parent.id) AS parents", Map.of("id", id.toString()), false).single();
            grants.add(new ControlledGrant(id, new LinkedHashSet<>(parents.get("parents").asList(parent -> UUID.fromString(parent.asString())))));
        }
        return new GrantContext(!row.get("object").isNull(), !row.get("giver").isNull(), !row.get("recipient").isNull(), sources, grants);
    }

    /**
     * Helper: add list property from a node into a target set.
     * Comments in English as requested.
     */
    private static void addAllStrings(Set<String> target, org.neo4j.driver.types.Node node, String key) {
        if (!node.containsKey(key) || node.get(key).isNull()) return;

        // Expected: list of strings (like [] / ["id","name"]) :contentReference[oaicite:5]{index=5}
        try {
            for (String s : node.get(key).asList(val -> val.asString())) {
                if (s != null && !s.isBlank()) target.add(s);
            }
        } catch (Exception ignored) {
            // If somehow stored as single string, fallback:
            String s = node.get(key).asString(null);
            if (s != null && !s.isBlank()) target.add(s);
        }
    }

    public List<ObjectAttributeExtendedClazz> getRightsList(Set<UUID> objectIds, UUID identityId, UUID requestedById, UUID applicationId) {
        if (objectIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<ObjectAttributeExtendedClazz> rightsList = new ArrayList<>();

        for (UUID id : objectIds) {
            Optional<ObjectAttributeExtendedClazz> result = getRights(applicationId, identityId, requestedById, id);
            result.ifPresent(rightsList::add);
        }
        return rightsList;
    }

    public StringBuilder getRightsQuery(UUID applicationId, UUID identityId, UUID requestedById, UUID oId, String oaName, String i1Name, String i2Name) {
        StringBuilder builder = new StringBuilder();

        builder.append(" (").append(i2Name).append(":I {id: '").append(identityId).append("'})-[rel:relation {access:1}]->" + "(").append(oaName).append(":OA)<-[:assigned]-(o:O {id: '").append(oId).append("'})");
        //integration of application
        builder.append("<-[:entity]-(e:E)<-[:application]-(a:A {id: '").append(applicationId).append("'})");

        if (!identityId.equals(requestedById)) {
            builder.append(" ,(").append(i1Name).append(":I {id: '").append(requestedById).append("'})-[rel2:relation {control:1}]->(").append(oaName).append(")");
        }


        return builder;
    }

    public Optional<ObjectAttributeExtendedClazz> getRights(UUID applicationId, UUID identityId, UUID requestedById, UUID oId) {
        StringBuilder builder = new StringBuilder();
        builder.append("MATCH");
        builder.append(getRightsQuery(applicationId, identityId, requestedById, oId, "oa", "i1", "i2"));
        builder.append(" RETURN oa, o.id, o.entityClass");

        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }
        var r = parseResult.parseOAResultNodes(result, true, false);
        if (r.size() > 1) {

        }
        return Optional.of(r.get(0));
    }

    public Optional<ObjectAttributeExtendedClazz> getRightsWithDigitAccess(UUID applicationId, UUID identityId, UUID requestedById, UUID oId) {
        StringBuilder builder = new StringBuilder();

        builder.append("MATCH");
        builder.append(" (").append("iChild").append(":I {id: '").append(identityId).append("'})-[rel:relation {access:1}]->" + "(").append("oa").append(":OA)<-[:assigned]-(o:O {id: '").append(oId).append("'})<-[:entity]-(e:E)<-[:application]-(a:A {id: '").append(applicationId).append("'})");

        //other identity calls rights for an identity-> Check if the callingIdentity can control the Access
        if (!identityId.equals(requestedById)) {
            builder.append(" ,(").append("iParent").append(":I {id: '").append(requestedById).append("'})-[rel2:relation {control:1}]->(").append("oa").append(")");
        }
        builder.append(" WITH oa, o ");
        builder.append(" OPTIONAL MATCH (oa)-[:digitAccess]->(da:DA) ");
        builder.append(" RETURN oa, o, collect(da) AS das");


        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }


        //keys must matching the Cypher RETURN
        String[] keys = {"oa", "o", "das"};
        var r = parseResult.parseMixedResultNodesWithDigitAccess(result, keys, false);

        var validatedResult = accessValidator.combineAllOaWithDigitsAccessForReadingAccess(r);

        if (r.size() > 1) {

        }
        return Optional.of(validatedResult);
    }

    public Optional<ObjectAttributeClazz> findOaById(UUID id) {
        StringBuilder builder = new StringBuilder();

        builder.append(" MATCH (oa:").append("OA");
        builder.append(" {id: '").append(id).append("'}");
        builder.append(") <-[:assigned]-(o:O)");
        builder.append(" RETURN oa, o.id, o.entityClass");
        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }
        var r = parseResult.parseOAResultNodes(result, false, false);
        if (r.size() > 1) {

        }
        var oa = Optional.of(r.get(0)).get();

        var oaNew = new ObjectAttributeClazz();

        oaNew.setId(oa.getId());
        oaNew.setLabels(oa.getLabels());
        oaNew.setEntityClass(oa.getEntityClass());
        oaNew.setType(oa.getType());
        oaNew.setName(oa.getName());

        oaNew.setReadProperties(oa.getReadProperties());
        oaNew.setWriteProperties(oa.getWriteProperties());
        oaNew.setShareReadProperties(oa.getShareReadProperties());
        oaNew.setShareWriteProperties(oa.getShareWriteProperties());
        oaNew.setDigitsAccess(oa.getDigitsAccess());

        return Optional.of(oaNew);
    }

    public Optional<ObjectAttributeClazz> findOaByIdWithDigitAccess(UUID id) {
        StringBuilder builder = new StringBuilder();

        builder.append(" MATCH (oa:").append("OA");
        builder.append(" {id: '").append(id).append("'}");
        builder.append(") <-[:assigned]-(o:O)");
        builder.append(" WITH oa, o ");
        builder.append(" OPTIONAL MATCH (oa)-[:digitAccess]->(da:DA) ");
        builder.append(" RETURN oa, o, collect(da) AS das");


        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }


        //keys must matching the Cypher RETURN
        String[] keys = {"oa", "o", "das"};
        var r = parseResult.parseMixedResultNodesWithDigitAccess(result, keys, false);

        var validatedResult = accessValidator.combineAllOaWithDigitsAccessForReadingAccess(r);

        if (r.size() > 1) {

        }

        var parsedResultToOa = parseResult.parseObjectAttributeNodeFromExtended(validatedResult);

        return Optional.of(parsedResultToOa);
    }

    public Optional<ObjectAttributeExtendedClazz> updateOa(ObjectAttributeClazz oa) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("oaId", oa.getId().toString());
        parameters.put("read", new ArrayList<>(oa.getReadProperties()));
        parameters.put("write", new ArrayList<>(oa.getWriteProperties()));
        parameters.put("shareRead", new ArrayList<>(oa.getShareReadProperties()));
        parameters.put("shareWrite", new ArrayList<>(oa.getShareWriteProperties()));
        parameters.put("digitRanges", digitRows(oa.getDigitsAccess()));
        String query = "MATCH (oa:OA {id:$oaId})<-[:assigned]-(o:O) "
                + "SET oa.readProperties=$read, oa.writeProperties=$write, "
                + "oa.shareReadProperties=$shareRead, oa.shareWriteProperties=$shareWrite "
                + "WITH oa,o OPTIONAL MATCH (oa)-[:digitAccess]->(old:DA) "
                + "WITH oa,o,collect(old) AS oldDigits "
                + "FOREACH (old IN oldDigits | DETACH DELETE old) "
                + createDigitsClause()
                + "WITH oa,o OPTIONAL MATCH (oa)-[:digitAccess]->(da:DA) RETURN oa,o,collect(da) AS das";
        var result = graphRepository.execute(query, parameters, false);
        if (!result.hasNext()) return Optional.empty();
        return Optional.of(parseResult.parseMixedResultNodesWithDigitAccess(result, new String[]{"oa", "o", "das"}, false).get(0));
    }

    public void updateOaRecursiv(ObjectAttributeClazz oa) {

        StringBuilder builder = new StringBuilder();
        builder.append(" MATCH (oa:").append("OA");
        builder.append(" {id: '").append(oa.getId()).append("'}");
        builder.append(")");

        var rP = oa.getReadProperties().stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));
        builder.append(" SET oa.readProperties = ").append(rP);


        rP = oa.getWriteProperties().stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));
        builder.append("  ,oa.writeProperties = ").append(rP);


        rP = oa.getShareReadProperties().stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));

        builder.append("  ,oa.shareReadProperties = ").append(rP);

        rP = oa.getShareWriteProperties().stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));
        builder.append("  ,oa.shareWriteProperties = ").append(rP);
        builder.append(" RETURN oa");

        var result = graphRepository.execute(builder.toString(), false);
    }

    public void createOa(ObjectAttributeClazz oa) {
        StringBuilder builder = generateCreateOaQuery(oa);
        builder.append(" ");
        builder.append("RETURN oa");
        var result = graphRepository.execute(builder.toString(), false);
    }

    public StringBuilder generateCreateOaQuery(ObjectAttributeClazz oa) {

        StringBuilder builder = new StringBuilder();
        builder.append(" CREATE (oa:").append("OA");
        builder.append(" {id: '").append(oa.getId()).append("'");
        builder.append(", entityClass: ").append("'OA'");
        builder.append(", type: ").append("'OA'");
        builder.append(", name: '").append(oa.getName()).append("'");

        var rP = oa.getReadProperties().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ", "[", "]"));
        builder.append(",readProperties:").append(rP);

        rP = oa.getWriteProperties().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ", "[", "]"));
        builder.append(",writeProperties:").append(rP);

        rP = oa.getShareReadProperties().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ", "[", "]"));
        builder.append(",shareReadProperties:").append(rP);

        rP = oa.getShareWriteProperties().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ", "[", "]"));
        builder.append(",shareWriteProperties:").append(rP);

        builder.append("})");

        return builder;
    }

    public Optional<ObjectAttributeExtendedClazz> createAccess(UUID applicationId, ObjectAttributeClazz oa, UUID oaParent, UUID identityId, UUID requestedById, UUID oId) {
        StringBuilder builder = new StringBuilder();

        //find O node
        builder.append("MATCH (o:O {id: '").append(oId).append("'})").append("<-[:entity]-(e:E)<-[:application]-(a:A {id: '").append(applicationId).append("'})").append(", ");

        //find requestedByIdentity node
        builder.append("(i1:I {id: '").append(requestedById).append("'})").append(", ");


        //find identityIdentity node
        builder.append("(i2:I {id: '").append(identityId).append("'})").append(", ");


        //find parent OA node
        builder.append("(oa1:OA {id: '").append(oaParent).append("'})").append(" ");

        //create OA node
        builder.append(generateCreateOaQuery(oa));
        builder.append(" ");

        //create relation between parentOA and OA
        builder.append("CREATE (oa)-[:rights]->(oa1)").append(" ");

        //create relation O and OA
        builder.append("CREATE (o)-[:assigned]->(oa)").append(" ");

        //create relation identityId and OA
        builder.append("CREATE (i1)-[:relation {owns:0,access:0,control:1}]->(oa)").append(" ");

        //create relation requestedById and OA
        builder.append("CREATE (i2)-[:relation {owns:0,access:1,control:0}]->(oa)").append(" ");

        builder.append(createDigitsClause()).append("RETURN oa, o");


        var result = graphRepository.execute(builder.toString(), Map.of("digitRanges", digitRows(oa.getDigitsAccess())), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }


        String[] keys = {"oa", "o"};
        var rList = parseResult.parseMixedResultNodesWithDigitAccess(result, keys, false);


        var r = accessValidator.combineAllOaForReadingAccess(rList);


        return Optional.of(r);
    }

    public Boolean createObject(UUID applicationId, UUID identityId, ObjectClazz oNode, ObjectAttributeClazz oaNode) {

        StringBuilder builder = new StringBuilder();

        String query = String.format(
                // 1. Match application and entity
                "MATCH (a:A {id: '%s'})-[:application]->(e:E {entityClass: '%s'}) " +
                        // 2. Create new object node, even if same ID exists elsewhere
                        "CREATE (o:O {id: '%s', name: '%s', entityClass: '%s', type: '%s'}) " +
                        // 3. Connect entity to object
                        "MERGE (e)-[:entity]->(o) " +
                        // 4. Create OA node
                        "WITH o, e, a " +
                        generateCreateOaQuery(oaNode) + // WITH o, e, a, oa
                        "WITH o, e, a, oa " +
                        // 5. Connect object to OA
                        "MERGE (o)-[:assigned]->(oa) " +
                        "WITH o, e, a, oa " +
                        // 6. Match identity
                        "MATCH (i:I {id: '%s'}) " +
                        "WITH o, e, a, i, oa " +
                        // 7. Connect identity to OA
                        "MERGE (i)-[:relation {access:1, control:1, owns:1}]->(oa) " +
                        // 8. Return new object
                        "RETURN o",
                applicationId.toString(),
                oNode.getEntityClass(),
                oNode.getId().toString(),
                oNode.getName(),
                oNode.getEntityClass(),
                oNode.getType(),
                identityId.toString()
        );
        builder.append(query);

        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return false;
        }
        return true;
    }

    public Boolean createEntity(UUID applicationId, EntityClazz eNode) {

        StringBuilder builder = new StringBuilder();


        String eProperties = String.format(
                "{id: '%s', entityClass: '%s', type: '%s', name: '%s'}",
                eNode.getId(), eNode.getEntityClass(), eNode.getType(), eNode.getName()
        );


        String query = String.format(
                "MERGE (a:A {id: '%s'}) " +
                        "MERGE (e:E %s) " +
                        "MERGE (a)-[:application]->(e) " +
                        "RETURN e",
                applicationId.toString(),
                eProperties
        );

        builder.append(query);

        var result = graphRepository.execute(builder.toString(), false);

        if (!result.hasNext()) {
            return false;
        }

        return true;
    }

    public Optional<List<ObjectAttributeExtendedClazz>> checkIfAllNodesExistsForObject(UUID applicationId, UUID identityId, UUID oId, String entityClass) {

        StringBuilder builder = new StringBuilder();


        String query = String.format("OPTIONAL MATCH (i:I {id: '%s'}) " + "WITH i " + "OPTIONAL MATCH (o:O {id: '%s'})<-[:entity]-(e:E)<-[:application]-(a:A {id: '%s'}) "
                + "WITH i, o " + "OPTIONAL MATCH (a:A {id: '%s'})-[:application]->(e:E {entityClass: '%s'}) "
                + "RETURN o, i, e", identityId, oId, applicationId, applicationId, entityClass // Assuming i5's ID is meant to be `requestedById` based on your initial setup
        );

        // Append the constructed query
        builder.append(query);

        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }
        //keys must matching the Cypher RETURN
        String[] keys = {"o", "i", "e"};
        var r = parseResult.parseMixedResultNodesForCheckAccess(result, keys, false);
        if (r.size() > 1) {

        }
        return Optional.of(r);
    }

    public Optional<List<ObjectAttributeExtendedClazz>> checkIfAllNodesExistsForAccess(UUID applicationId, UUID identityId, UUID requestedById, UUID oId) {

        StringBuilder builder = new StringBuilder();

        //oa1= oaCallingRights = parent or myself
        //oa3= oaChangingRights = current access which wants to changed (child)

        String query = String.format("OPTIONAL MATCH (i2:I {id: '%s'}) " +
                "WITH i2 " + "OPTIONAL MATCH (i1:I {id: '%s'})-[rel:relation]->(oa1:OA)<-[:assigned]-(o:O {id: '%s'}) " +
                "WHERE rel.access = 1 " + "WITH i1, i2, oa1, o " +
                "OPTIONAL MATCH (i8:I {id: '%s'})-[rel5:relation]->(oa2:OA)<-[:assigned]-(o)<-[:entity]-(e:E)<-[:application]-(a:A {id: '%s'}) " +
                "WHERE rel5.access = 1 " + "WITH i1, i2, oa1, o, oa2 " +
                "OPTIONAL MATCH (i5:I {id: '%s'})-[rel2:relation]->(oa3:OA)-[:rights]->(oa1) " +
                "WHERE rel2.control = 1 AND oa2=oa3 ", identityId, requestedById, oId, identityId, applicationId, requestedById
        );

        //keys must matching the Cypher RETURN
        String[] keys = {"o", "i1", "i2", "oa1", "oa3"};

        // Append the constructed query
        builder.append(query);

        var returnQuery = String.join(", ", keys);
        builder.append("RETURN ").append(returnQuery);


        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }

        var r = parseResult.parseMixedResultNodesForCheckAccess(result, keys, true);
        if (r.size() > 1) {

        }
        return Optional.of(r);
    }

    public void deleteDigitAccess(UUID oaId) {
        StringBuilder builder = new StringBuilder();

        builder.append("MATCH (oa").append(":OA {id: '").append(oaId.toString()).append("'})-[:digitAccess]->(da:DA) DETACH DELETE da");
        var result = graphRepository.execute(builder.toString(), false);
    }

    public void createDigitAccess(Set<DigitsAccessDTO> digitsAccess, UUID oaId) {
        String query = "MATCH (oa:OA {id:$oaId}) OPTIONAL MATCH (oa)-[:digitAccess]->(old:DA) "
                + "WITH oa,collect(old) AS oldDigits FOREACH (old IN oldDigits | DETACH DELETE old) "
                + createDigitsClause() + "RETURN oa.id";
        graphRepository.execute(query, Map.of("oaId", oaId.toString(), "digitRanges", digitRows(digitsAccess)), false);
    }

    public Optional<List<DigitAccessClazz>> getDigitAccess(UUID oaId) {

        StringBuilder builder = new StringBuilder();

        //oa1= oaCallingRights = parent or myself
        //oa3= oaChangingRights = current access which wants to changed (child)

        String query = String.format("OPTIONAL MATCH (oa:OA {id:'%s'})-[:digitAccess]->(da:DA) ", oaId);

        //keys must matching the Cypher RETURN
        String[] keys = {"da"};

        // Append the constructed query
        builder.append(query);

        var returnQuery = String.join(", ", keys);
        builder.append("RETURN ").append(returnQuery);


        var result = graphRepository.execute(builder.toString(), false);
        if (!result.hasNext()) {
            return Optional.empty();
        }

        var r = parseResult.parseDigitAccessNodes(result, keys);
        if (r.size() > 1) {

        }
        return Optional.of(r);
    }

    public List<ObjectAttributeExtendedClazz> getRightsListOld(UUID applicationId, Set<UUID> objectIds, UUID identityId, UUID requestedById) {
        StringBuilder builder = new StringBuilder();
        if (objectIds.isEmpty()) {
            return new ArrayList<>();
        }
        builder.append("MATCH (i:I {id:'").append(identityId).append("'})-[rel:relation {access:1}]->(oa:OA)<-[:assigned]-(o:O)<-[:entity]-(e:E)<-[:application]-(a:A {id: '").append(applicationId).append("'})");
        if (!identityId.equals(requestedById)) {
            builder.append(" , (i1:I {id: '").append(requestedById).append("'})-[relControl:relation {control:1}" + "]->(oa)");
        }

        builder.append(" WHERE  o.id IN [");

        for (UUID id : objectIds) {
            builder.append(" '").append(id).append("', ");
        }
        String cypher = builder.toString();
        cypher = cypher.substring(0, cypher.length() - 2);
        cypher += "] WITH DISTINCT oa,o OPTIONAL MATCH (oa)-[:digitAccess]->(da:DA) RETURN oa,o,collect(da) AS das";
        var result = graphRepository.execute(cypher, false);
        return mergeByObject(parseResult.parseMixedResultNodesWithDigitAccess(result, new String[]{"oa", "o", "das"}, false));
    }

    public List<ObjectAttributeExtendedClazz> getRightsClass(UUID applicationId, String entityClazz, UUID requestedById, boolean createdByMyOwn, UUID identityId, Integer pagesize) {
        StringBuilder builder = new StringBuilder();

        if (identityId == null) {
            if (createdByMyOwn) {
                builder.append("MATCH (i:I {id: '").append(requestedById).append("'})-[rel:relation {owns:1}]-" + "(oa:OA)-[:assigned]-(o:O)-[:entity]-(e:E {entityClass:'").append(entityClazz).append("'})");
            } else {
                builder.append("MATCH (i:I {id: '").append(requestedById).append("'})-[rel:relation {access:1}]-" + "(oa:OA)-[:assigned]-(o:O)-[:entity]-(e:E {entityClass:'").append(entityClazz).append("'})");
            }
        } else {
            if (createdByMyOwn) {
                builder.append("MATCH (i:I {id: '").append(requestedById).append("'})-[relOWS:relation {owns:1}]-" + "(oaOWS:OA)-[:assigned]-(o:O)-[:entity]-(e:E {entityClass:'").append(entityClazz).append("'})");
                builder.append(", (i2:I {id: '").append(identityId).append("'})-[rel:relation {access:1}]-" + "(oa:OA)-[:assigned]-(o:O)-[:entity]-(e:E {entityClass:'").append(entityClazz).append("'})");
                builder.append(", (i:I {id: '").append(requestedById).append("'})-[rel2:relation {control:1}" + "]->(oa) ");
            } else {
                builder.append("MATCH (i:I {id: '").append(identityId).append("'})-[rel:relation {access:1}]-" + "(oa:OA)-[:assigned]-(o:O)-[:entity]-(e:E {entityClass:'").append(entityClazz).append("'})");
                builder.append(", (i1:I {id: '").append(requestedById).append("'})-[rel2:relation {control:1}" + "]->(oa) ");
            }
        }

        builder.append(", (:A {id:'").append(applicationId).append("'})-[:application]->(e) ");
        builder.append(" WITH DISTINCT oa,o OPTIONAL MATCH (oa)-[:digitAccess]->(da:DA) RETURN oa,o,collect(da) AS das");
        builder.append(" LIMIT " + pagesize.toString());

        log.error(builder.toString());
        var result = graphRepository.execute(builder.toString(), false);
        return mergeByObject(parseResult.parseMixedResultNodesWithDigitAccess(result, new String[]{"oa", "o", "das"}, false));
    }

    public List<ObjectAttributeExtendedClazz> getAllMyRights(UUID requestedById) {
        StringBuilder builder = new StringBuilder();
        builder.append("MATCH (i:I {id: '").append(requestedById).append("'})-[rel:relation {access:1}]->(oa:OA)<-[:assigned]-(o:O)");

        builder.append(" RETURN oa, o.id, o.entityClass");
        var result = graphRepository.execute(builder.toString(), false);
        return parseResult.parseOAResultNodes(result, false, false);
    }

    public List<ObjectAttributeExtendedClazz> addPropertyToEntityByIdentity(UUID applicationId, UUID requestedById, String entityClass, String newProperty) {

        List<ObjectAttributeExtendedClazz> resultList = new ArrayList<>();
        Set<String> propertiesList = new HashSet<>(Arrays.asList("readProperties", "writeProperties", "shareReadProperties", "shareWriteProperties"));

        // This builds the following query for each property:
        // MATCH (i:I {id: '11c408e0-1fcd-11ee-be56-0000000000'})-[:relation]->(oa:OA)<-[:assigned]-(o:O)<-[:entity]-(e:E {entityClass: 'ExampleClass'})
        // WHERE NOT 'e' IN oa.readProperties
        // SET oa.readProperties = coalesce(oa.readProperties + 'e', ['e']),

        propertiesList.forEach(properties -> {

            StringBuilder builder = new StringBuilder();
            builder.append("MATCH (i:I {id: '").append(requestedById).append("'})-[rel:relation {owns:1}]->(oa:OA)<-[:assigned]-(o:O)");
            builder.append("<-[:entity]-(e:E {entityClass: '").append(entityClass).append("'})<-[:application]-(a:A {id: '").append(applicationId).append("'}) ");
            builder.append("WHERE NOT '").append(newProperty).append("' IN oa.").append(properties).append(" ");
            builder.append("SET oa.").append(properties).append(" = coalesce(oa.").append(properties).append(" + '").append(newProperty).append("', ['").append(newProperty).append("']);");

            var result = graphRepository.execute(builder.toString(), false);
            resultList.addAll(parseResult.parseOAResultNodes(result, false, true));

        });
        return resultList;
    }

    public List<ObjectAttributeExtendedClazz> addPropertyToEntityGlobal(String entityClass, String newProperty) {

        List<ObjectAttributeExtendedClazz> resultList = new ArrayList<>();
        Set<String> propertiesList = new HashSet<>(Arrays.asList("readProperties", "writeProperties", "shareReadProperties", "shareWriteProperties"));

        // This builds the following query for each property:
        // MATCH (i:I)-[:relation]->(oa:OA)<-[:assigned]-(o:O)<-[:entity]-(e:E {entityClass: 'ExampleClass'})
        // WHERE NOT 'e' IN oa.readProperties
        // SET oa.readProperties = coalesce(oa.readProperties + 'e', ['e']),

        propertiesList.forEach(properties -> {

            StringBuilder builder = new StringBuilder();
            builder.append("MATCH (i:I)-[rel:relation {owns:1}]->(oa:OA)<-[:assigned]-(o:O)");
            builder.append("<-[:entity]-(e:E {entityClass: '").append(entityClass).append("'}) ");
            builder.append("WHERE NOT '").append(newProperty).append("' IN oa.").append(properties).append(" ");
            builder.append("SET oa.").append(properties).append(" = coalesce(oa.").append(properties).append(" + '").append(newProperty).append("', ['").append(newProperty).append("']);");

            var result = graphRepository.execute(builder.toString(), false);
            resultList.addAll(parseResult.parseOAResultNodes(result, false, true));

        });
        return resultList;
    }

    public List<ObjectAttributeExtendedClazz> renamePropertyOfEntityByIdentity(UUID applicationId, UUID requestedById, String entityClass, String oldProperty, String newProperty) {

        List<ObjectAttributeExtendedClazz> resultList = new ArrayList<>();
        Set<String> propertiesList = new HashSet<>(Arrays.asList("readProperties", "writeProperties", "shareReadProperties", "shareWriteProperties"));

        // This builds the following query for each property:
        // MATCH (i:I {id: '11c408e0-1fcd-11ee-be56-0242ac120005'})-[rel:relation {owns:1}]->(oa:OA)<-[:assigned]-(o:O)<-[:entity]-(e:E {entityClass: 'Motorbike'})
        // WITH oa, o, e
        // MATCH (o)-[:assigned]->(assigned_oa:OA)
        // SET assigned_oa.readProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.readProperties THEN
        //      [prop IN assigned_oa.readProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.readProperties
        //  END,
        //  assigned_oa.writeProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.writeProperties THEN
        //      [prop IN assigned_oa.writeProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.writeProperties
        //  END,
        //  assigned_oa.shareReadProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.shareReadProperties THEN
        //      [prop IN assigned_oa.shareReadProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.shareReadProperties
        //  END,
        //  assigned_oa.shareWriteProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.shareWriteProperties THEN
        //      [prop IN assigned_oa.shareWriteProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.shareWriteProperties
        //  END

        StringBuilder builder = new StringBuilder();
        builder.append("MATCH (i:I {id: '").append(requestedById).append("'})-[rel:relation {owns:1}]->(oa:OA)<-[:assigned]-(o:O)");
        builder.append("<-[:entity]-(e:E {entityClass: '").append(entityClass).append("'})<-[:application]-(a:A {id: '").append(applicationId).append("'}) ");
        builder.append("WITH oa, o ");
        builder.append("MATCH (o)-[:assigned]->(assigned_oa:OA) ");
        builder.append("SET ");

        propertiesList.forEach(properties -> {

            builder.append("assigned_oa.").append(properties).append(" = ");
            builder.append("CASE ");
            builder.append("WHEN '").append(oldProperty).append("' IN assigned_oa.").append(properties).append(" THEN ");
            builder.append("[prop IN assigned_oa.").append(properties).append(" | CASE WHEN prop = '").append(oldProperty).append("' THEN '").append(newProperty).append("' ELSE prop END] ");
            builder.append("ELSE assigned_oa.").append(properties).append(" ");
            builder.append("END");

            if (properties == "shareWriteProperties") {
                builder.append(";");
            } else {
                builder.append(", ");
            }

        });

        var result = graphRepository.execute(builder.toString(), false);
        resultList.addAll(parseResult.parseOAResultNodes(result, false, true));
        return resultList;
    }

    public List<ObjectAttributeExtendedClazz> renamePropertyOfEntityGlobal(String entityClass, String oldProperty, String newProperty) {

        List<ObjectAttributeExtendedClazz> resultList = new ArrayList<>();
        Set<String> propertiesList = new HashSet<>(Arrays.asList("readProperties", "writeProperties", "shareReadProperties", "shareWriteProperties"));

        // This builds the following query for each property:
        // MATCH (i:I)-[rel:relation {access:1}]->(oa:OA)<-[:assigned]-(o:O)<-[:entity]-(e:E {entityClass: 'Motorbike'})
        // WITH oa, o, e
        // MATCH (o)-[:assigned]->(assigned_oa:OA)
        // SET assigned_oa.readProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.readProperties THEN
        //      [prop IN assigned_oa.readProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.readProperties
        //  END,
        //  assigned_oa.writeProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.writeProperties THEN
        //      [prop IN assigned_oa.writeProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.writeProperties
        //  END,
        //  assigned_oa.shareReadProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.shareReadProperties THEN
        //      [prop IN assigned_oa.shareReadProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.shareReadProperties
        //  END,
        //  assigned_oa.shareWriteProperties =
        //  CASE
        //    WHEN 'doors' IN assigned_oa.shareWriteProperties THEN
        //      [prop IN assigned_oa.shareWriteProperties | CASE WHEN prop = 'doors' THEN 'door' ELSE prop END]
        //    ELSE assigned_oa.shareWriteProperties
        //  END

        StringBuilder builder = new StringBuilder();
        builder.append("MATCH (i:I)-[rel:relation {access:1}]->(oa:OA)<-[:assigned]-(o:O)");
        builder.append("<-[:entity]-(e:E {entityClass: '").append(entityClass).append("'}) ");
        builder.append("WITH oa, o ");
        builder.append("MATCH (o)-[:assigned]->(assigned_oa:OA) ");
        builder.append("SET ");

        propertiesList.forEach(properties -> {

            builder.append("assigned_oa.").append(properties).append(" = ");
            builder.append("CASE ");
            builder.append("WHEN '").append(oldProperty).append("' IN assigned_oa.").append(properties).append(" THEN ");
            builder.append("[prop IN assigned_oa.").append(properties).append(" | CASE WHEN prop = '").append(oldProperty).append("' THEN '").append(newProperty).append("' ELSE prop END] ");
            builder.append("ELSE assigned_oa.").append(properties).append(" ");
            builder.append("END");

            if (properties == "shareWriteProperties") {
                builder.append(";");
            } else {
                builder.append(", ");
            }

        });

        var result = graphRepository.execute(builder.toString(), false);
        resultList.addAll(parseResult.parseOAResultNodes(result, false, true));
        return resultList;
    }

    //get all access recursive for an object based on my oa down
    public Optional<List<IdentityObjectRightsDTO>> getAllRightsForObjectWithDigitAccess(
            UUID applicationId,
            UUID requestedById,
            UUID oId
    ) {
        StringBuilder b = new StringBuilder();

        b.append("MATCH (a:A {id:'").append(applicationId).append("'})-[:application]->(:E)-[:entity]->(o:O {id:'")
                .append(oId).append("'}) ");

        b.append("MATCH (caller:I {id:'").append(requestedById).append("'})-[rCaller:relation]->(myOa:OA)<-[:assigned]-(o) ")
                .append("WHERE rCaller.access = 1 ");

        // --- NEW: build candidate identities via subquery + UNION
        // Part 1: original candidates (must reach myOa via rights chain)
        // Part 2: always include the "top/owner" identity (13d4...) if it has access=1 on an OA assigned to this object
        b.append("CALL { ");
        b.append("  WITH o, myOa ");
        b.append("  MATCH (i:I)-[rAcc:relation]->(oaAcc:OA) ");
        b.append("  WHERE rAcc.access = 1 ");
        b.append("  MATCH (oaAcc)-[:rights*0..]->(myOa) ");
        b.append("  MATCH (oaAcc)<-[:assigned]-(o) ");
        b.append("  RETURN i, oaAcc ");
        b.append("  UNION ");
        b.append("  WITH o ");
        b.append("  MATCH (i:I {id:'").append(requestedById).append("'})-[rAcc:relation]->(oaAcc:OA)<-[:assigned]-(o) ");
        b.append("  WHERE rAcc.access = 1 ");
        b.append("  RETURN i, oaAcc ");
        b.append("} ");

        // Collect OA nodes per identity
        b.append("WITH o, i, collect(DISTINCT oaAcc) AS oas ");

        b.append("UNWIND oas AS oneOa ");
        b.append("OPTIONAL MATCH (from:I)-[rFrom:relation]->(oneOa) ")
                .append("WHERE rFrom.control = 1 ");

        b.append("WITH o, i, oas, head(collect(DISTINCT from)) AS sharedFromIdentity ");

        b.append("OPTIONAL MATCH (i)-[rICtrl:relation]->(ctrlOa:OA)<-[:assigned]-(o) ")
                .append("WHERE rICtrl.control = 1 ");

        b.append("OPTIONAL MATCH (to:I)-[rToAcc:relation]->(ctrlOa) ")
                .append("WHERE (rToAcc.access = 1) AND to.id <> i.id ");

        // IMPORTANT: return "toIds" to match your Java mapping (record.get(\"toIds\"))
        b.append("WITH o, i, oas, sharedFromIdentity, collect(DISTINCT to.id) AS toIds ");

        b.append("WITH o, i, oas, sharedFromIdentity, toIds, ")
                .append("CASE WHEN i.id = '").append(requestedById).append("' THEN 1 ELSE 0 END AS isCaller ");

        b.append("RETURN i AS identity, o AS object, oas AS oas, sharedFromIdentity, toIds ");
        b.append("ORDER BY isCaller DESC, identity.id ");


        var result = graphRepository.execute(b.toString(), false);
        if (!result.hasNext()) return Optional.empty();

        List<IdentityObjectRightsDTO> out = new ArrayList<>();

        while (result.hasNext()) {
            var record = result.next();

            var identityNode = record.get("identity").asNode();
            UUID identityId = UUID.fromString(identityNode.get("id").asString());

            var objectNode = record.get("object").asNode();
            UUID objectId = UUID.fromString(objectNode.get("id").asString());
            String entityClass = objectNode.get("entityClass").asString();

            IdentityObjectRightsDTO dto = new IdentityObjectRightsDTO();
            dto.setIdentityId(identityId);
            dto.setObjectId(objectId);
            dto.setEntityClass(entityClass);

            // sharedFromIdentityId = controller.id (if present)
            if (!record.get("sharedFromIdentity").isNull()) {
                var ctrlNode = record.get("sharedFromIdentity").asNode();
                dto.setSharedFromIdentityId(UUID.fromString(ctrlNode.get("id").asString()));
            }

            // sharedToIdentityIds = toIds (UUID list), optional: remove self
            if (!record.get("toIds").isNull()) {
                Set<UUID> sharedTo = new HashSet<>();
                for (var v : record.get("toIds").values()) {
                    if (v == null || v.isNull()) continue;
                    UUID toId = UUID.fromString(v.asString());
                    if (!toId.equals(identityId)) sharedTo.add(toId); // optional: exclude self
                }
                dto.setSharedToIdentityIds(sharedTo);
            }

            List<ObjectAttributeExtendedClazz> grants = new ArrayList<>();
            for (var value : record.get("oas").values()) {
                var oa = findOaByIdWithDigitAccess(UUID.fromString(value.asNode().get("id").asString()));
                oa.ifPresent(node -> grants.add(new OADeserializer().parseOAtoOAExtended(node)));
            }
            var combined = accessValidator.combineAllOaWithDigitsAccessForReadingAccess(grants);
            if (combined != null) {
                dto.setReadProperties(combined.getReadProperties());
                dto.setWriteProperties(combined.getWriteProperties());
                dto.setShareReadProperties(combined.getShareReadProperties());
                dto.setShareWriteProperties(combined.getShareWriteProperties());
                dto.setDigitsAccess(combined.getDigitsAccess());
            }

            out.add(dto);
        }

        return Optional.of(out);
    }


    /**
     * Helper that can handle different Neo4j value types (String, List, etc.)
     * and applies them to the corresponding OA property set.
     * <p>
     * NOTE: Comments in English as you prefer.
     */
    private void applyRightsValue(ObjectAttributeExtendedClazz oa, String targetKey, org.neo4j.driver.Value value) {
        if (value == null || value.isNull()) return;

        // Case 1: list of strings
        if (value.type().name().equalsIgnoreCase("LIST")) {
            for (String s : value.asList(v -> v.asString())) {
                if (s != null && !s.isBlank()) oa.addProperty(targetKey, s);
            }
            return;
        }

        // Case 2: single string (maybe JSON string)
        String s = value.asString(null);
        if (s == null || s.isBlank()) return;

        // If it's JSON map string, you can parse values:
        // Map<String,String> m = ObjectAttributeExtendedClazz.getPropertiesFromString(s);
        // if (m != null) m.values().forEach(v -> oa.addProperty(targetKey, v));
        //
        // Otherwise treat it as a single property:
        oa.addProperty(targetKey, s);
    }


    private String createDigitsClause() {
        return "FOREACH (properties IN $digitRanges | CREATE (da:DA) SET da=properties CREATE (oa)-[:digitAccess]->(da)) ";
    }

    private List<Map<String, Object>> digitRows(Set<DigitsAccessDTO> entries) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (entries == null) return rows;
        for (var entry : entries) {
            if (entry.getReadableDigits() == null) continue;
            for (var range : entry.getReadableDigits()) {
                String id = UUID.randomUUID().toString();
                rows.add(Map.of("id", id, "name", "da#" + id, "type", "DA", "property", entry.getProperty(),
                        "propertytype", entry.getType(), "digitstart", range.getReadableDigitsFrom(), "digitstop", range.getReadableDigitsTo()));
            }
        }
        return rows;
    }

    private List<ObjectAttributeExtendedClazz> mergeByObject(List<ObjectAttributeExtendedClazz> entries) {
        Map<UUID, List<ObjectAttributeExtendedClazz>> grouped = new LinkedHashMap<>();
        for (var entry : entries) grouped.computeIfAbsent(entry.getOId(), ignored -> new ArrayList<>()).add(entry);
        return grouped.values().stream().map(accessValidator::combineAllOaWithDigitsAccessForReadingAccess).toList();
    }
}

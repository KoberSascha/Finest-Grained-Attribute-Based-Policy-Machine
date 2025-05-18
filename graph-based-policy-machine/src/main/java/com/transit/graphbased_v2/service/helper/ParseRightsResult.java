package com.transit.graphbased_v2.service.helper;

import com.transit.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.transit.graphbased_v2.domain.graph.nodes.*;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import com.transit.graphbased_v2.repository.ObjectAttributeClazzRepository;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.internal.InternalNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.transit.graphbased_v2.service.helper.StringListHelper.listToSetString;
import static org.neo4j.driver.internal.value.NullValue.NULL;

@Component
public class ParseRightsResult {

    @Autowired
    private OADeserializer oADeserializer;
    @Autowired
    private DADeserializer dADeserializer;
    @Autowired
    private ObjectAttributeClazzRepository objectAttributeClazzRepository;

    @Autowired
    private AccessValidator accessValidator;


    @LogExecutionTime
    public List<ObjectAttributeExtendedClazz> parseOAResultNodes(Result result, boolean mergeAll, boolean mergeByOid) {
        List<ObjectAttributeExtendedClazz> results = new ArrayList<>();
        Set<UUID> objectIds = new HashSet<>();
        while (result.hasNext()) {
            var nextRecord = result.next();
            var r = parseOAResultNode(nextRecord, true);
            if (results.isEmpty()) {
                results.add(r);
                objectIds.add(r.getOId());
            } else if (mergeAll) {
                var temp = results.get(0);
                var temp2 = accessValidator.combineAccessPropertiesFromOa(r, temp);
                results.remove(0);
                results.add(temp2);
                objectIds.add(r.getOId());
            } else if (mergeByOid) {
                if (objectIds.contains(r.getOId())) {
                    var temp = results.stream().filter(e -> e.getOId().equals(r.getOId())).findFirst().get();
                    var temp2 = accessValidator.combineAccessPropertiesFromOa(r, temp);
                    results.replaceAll(e -> {
                        if (e.getOId().equals(temp2.getOId())) {
                            return temp2;
                        }
                        return e;
                    });

                } else {
                    results.add(r);
                    objectIds.add(r.getOId());
                }
            } else {
                results.add(r);
            }
        }
        return results;
    }

    @LogExecutionTime
    public ObjectAttributeExtendedClazz parseOAResultNode(Record record, boolean dataFromRecord) {
        ObjectAttributeExtendedClazz oa = new ObjectAttributeExtendedClazz();
        var temp = record.get(0);

        // get values from record
        if (dataFromRecord) {
            String idString = temp.get("id").asString();
            UUID id = UUID.fromString(idString);
            String name = temp.get("name").asString();
            String entityClass = temp.get("type").asString();

            List<Object> readProperties = temp.get("readProperties").asList();
            List<Object> writeProperties = temp.get("writeProperties").asList();
            List<Object> shareReadProperties = temp.get("shareReadProperties").asList();
            List<Object> shareWriteProperties = temp.get("shareWriteProperties").asList();

            Set<DigitsAccessDTO> digitsAccess = new HashSet<>();
            Set<String> rP = listToSetString(readProperties);
            Set<String> rPNew = new HashSet<>();


            Set<String> sReP = listToSetString(shareReadProperties);
            Set<String> sRePNew = new HashSet<>();


            oa.setReadProperties(rPNew);
            oa.setWriteProperties(listToSetString(writeProperties));
            oa.setShareReadProperties(sRePNew);
            oa.setShareWriteProperties(listToSetString(shareWriteProperties));
            oa.setDigitsAccess(digitsAccess);

            oa.setId(id);
            oa.setName(name);
            oa.setType(ClazzType.valueOf(entityClass));

            oa.setOId(UUID.fromString(record.get(1).asString()));
            oa.setOEntityClazz(record.get(2).asString());


        } else {

            var oaNormal = objectAttributeClazzRepository.findById(UUID.fromString(temp.get("id").asString())).get();
            oa.setId(oaNormal.getId());
            oa.setName(oaNormal.getName());
            oa.setType(oaNormal.getType());

            oa.setReadProperties(oaNormal.getReadProperties());
            oa.setWriteProperties(oaNormal.getWriteProperties());
            oa.setShareWriteProperties(oaNormal.getShareReadProperties());
            oa.setShareWriteProperties(oaNormal.getShareWriteProperties());
            oa.setOId(UUID.fromString(record.get(1).asString()));
        }

        return oa;
    }


    @LogExecutionTime
    public List<ObjectAttributeExtendedClazz> parseMixedResultNodesForCheckAccess(Result result, String[] keys, boolean mergeAll) {
        List<ObjectAttributeExtendedClazz> results = new ArrayList<>();

        List<Record> records = result.list();
        if (mergeAll) {
            int i = 0;
            for (Record record : records) {
                for (String key : keys) {
                    Value temp = record.get(key);
                    var c = parseMixedResultNode(temp, key);
                    if (i >= 1) {
                        for (ObjectAttributeExtendedClazz item : results) {
                            if (item.getName() == key) {
                                if ("OA".equals(c.getType().toString())) {
                                    //need to test - could be wrong
                                    c = accessValidator.combineAccessPropertiesFromOa(item, c);
                                }
                            }
                        }
                    } else {
                        results.add(c);
                    }

                }
                i++;
            }
        } else {
            // Check if there are any records
            if (!records.isEmpty()) {
                // Get the first record
                Record record = records.get(0);

                // Process each key in the provided keys array for the first record
                for (String key : keys) {
                    Value temp = record.get(key);
                    results.add(parseMixedResultNode(temp, key));
                }
            }
        }


        return results;
    }

    @LogExecutionTime
    public ObjectAttributeExtendedClazz parseMixedResultNode(Value temp, String nodeName) {
        ObjectAttributeExtendedClazz node = new ObjectAttributeExtendedClazz();

        if (temp == NULL) {
            String name = nodeName;
            String entityClass = "OA";
            node.setOEntityClazz("empty");
            node.setName(name);
            node.setType(ClazzType.valueOf(entityClass));
            return node;
        }


        String idString = temp.get("id").asString();
        UUID id = UUID.fromString(idString);
        String name = temp.get("name").asString();
        String entityClass = temp.get("type").asString();

        //just for error fix
        if ("UA".equals(entityClass) || nodeName.equals("i")) {
            entityClass = "I";
        }


        node.setId(id);
        node.setName(name);
        node.setType(ClazzType.valueOf(entityClass));

        if ("OA".equals(entityClass)) {
            node.setName(nodeName);

            List<Object> readProperties = temp.get("readProperties").asList();
            List<Object> writeProperties = temp.get("writeProperties").asList();
            List<Object> shareReadProperties = temp.get("shareReadProperties").asList();
            List<Object> shareWriteProperties = temp.get("shareWriteProperties").asList();

            Set<DigitsAccessDTO> digitsAccess = new HashSet<>();
            Set<String> rP = listToSetString(readProperties);
            Set<String> rPNew = new HashSet<>();

            for (String prop : rP) {
                if (prop.contains("{") || prop.contains("}")) {
                    var propDTO = oADeserializer.parseDigitsAccessFromString(prop, "readProperties");
                    rPNew.add(propDTO.getProperty());
                    digitsAccess.add(propDTO);
                } else {
                    rPNew.add(prop);
                }
            }

            Set<String> sReP = listToSetString(shareReadProperties);
            Set<String> sRePNew = new HashSet<>();
            for (String prop : sReP) {
                if (prop.contains("{") || prop.contains("}")) {
                    var propDTO = oADeserializer.parseDigitsAccessFromString(prop, "shareReadProperties");
                    sRePNew.add(propDTO.getProperty());
                    digitsAccess.add(propDTO);
                } else {
                    sRePNew.add(prop);
                }
            }


            node.setReadProperties(rPNew);
            node.setWriteProperties(listToSetString(writeProperties));
            node.setShareReadProperties(sRePNew);
            node.setShareWriteProperties(listToSetString(shareWriteProperties));
            node.setDigitsAccess(digitsAccess);

        }


        return node;
    }


    public List<DigitAccessClazz> parseDigitAccessNodes(Result result, String[] keys) {

        List<DigitAccessClazz> results = new ArrayList<>();
        List<Record> records = result.list();
        int i = 0;
        for (Record record : records) {
            for (String key : keys) {
                Value temp = record.get(key);
                if (!temp.isNull()) {
                    var c = parseDigitAccessNode(temp);
                    results.add(c);
                }
            }
        }
        return results;
    }


    @LogExecutionTime
    public List<ObjectAttributeExtendedClazz> parseMixedResultNodesWithDigitAccess(Result result, String[] keys, boolean mergeAll) {
        List<ObjectAttributeExtendedClazz> objectAccesses = new ArrayList<>();
        List<ObjectClazz> object = new ArrayList<>();


        List<Record> records = result.list();

        for (Record record : records) {

            ObjectAttributeExtendedClazz objectAccessTemp = new ObjectAttributeExtendedClazz();

            for (String key : keys) {
                Value temp = record.get(key);

                if (!temp.isNull()) {
                    if (!"das".equals(key)) {
                        String nodeType = temp.get("type").asString();
                        if ("OA".equals(nodeType)) {
                            objectAccessTemp = parseObjectAttributeNode(temp);

                            //default its an empty list
                            Set<DigitsAccessDTO> x = new HashSet<>();
                            objectAccessTemp.setDigitsAccess(x);
                        }


                        if ("O".equals(nodeType)) {
                            var o = parseObjectNode(temp);
                            object.add(o);
                            objectAccessTemp.setEntityClass(o.getEntityClass());
                            objectAccessTemp.setOId(o.getId());
                        }


                    } else {

                        //get all DA for OA
                        List<DigitAccessClazz> digitAccesses = new ArrayList<>();
                        List<Object> dasNodes = temp.asList();


                        for (Object node : dasNodes) {

                            var c = parseDigitAccessNodeFromObject(node);
                            digitAccesses.add(c);

                        }

                        //transform it into the DTO
                        Set<DigitsAccessDTO> daDTOs = dADeserializer.parseDigitsAccessFromDa(digitAccesses);

                        //update the OA and set the digitAccess
                        objectAccessTemp.setDigitsAccess(daDTOs);

                    }
                }

                //end of keys
            }

            //end of record
            objectAccesses.add(objectAccessTemp);
        }

        return objectAccesses;
    }

    @LogExecutionTime
    public ObjectClazz parseObjectNode(Value temp) {
        ObjectClazz node = new ObjectClazz();

        String idString = temp.get("id").asString();
        UUID id = UUID.fromString(idString);
        String name = temp.get("name").asString();
        String entityClass = temp.get("type").asString();

        node.setId(id);
        node.setName(name);
        node.setType(ClazzType.valueOf(entityClass));
        node.setEntityClass(temp.get("entityClass").asString());

        return node;
    }

    @LogExecutionTime
    public ObjectClazz parseObjectNodeFromResult(Result result) {

        if (!result.hasNext()) {
            throw new NoSuchElementException("No result found for application node.");
        }
        Record record = result.next();
        Value temp = record.get(0);
        ObjectClazz node = new ObjectClazz();

        String idString = temp.get("id").asString();
        UUID id = UUID.fromString(idString);
        String name = temp.get("name").asString();
        String entityClass = temp.get("type").asString();

        node.setId(id);
        node.setName(name);
        node.setType(ClazzType.valueOf(entityClass));
        node.setEntityClass(temp.get("entityClass").asString());

        return node;
    }

    @LogExecutionTime
    public ApplicationClazz parseApplicationNode(Result result) {
        if (!result.hasNext()) {
            throw new NoSuchElementException("No result found for application node.");
        }

        Record record = result.next();
        Value temp = record.get(0);

        ApplicationClazz node = new ApplicationClazz();

        UUID id = UUID.fromString(temp.get("id").asString());
        UUID identityId = UUID.fromString(temp.get("identityId").asString());
        String name = temp.get("name").asString();

        node.setId(id);
        node.setName(name);
        node.setIdentityId(identityId);

        return node;
    }

    @LogExecutionTime
    public List<ApplicationClazz> parseApplicationNodes(Result result) {
        List<ApplicationClazz> applications = new ArrayList<>();

        while (result.hasNext()) {
            Record record = result.next();
            Value temp = record.get("a"); // oder get(0) falls nur RETURN a

            ApplicationClazz node = new ApplicationClazz();
            UUID id = UUID.fromString(temp.get("id").asString());
            UUID identityId = UUID.fromString(temp.get("identityId").asString());
            String name = temp.get("name").asString();

            node.setId(id);
            node.setIdentityId(identityId);
            node.setName(name);

            applications.add(node);
        }

        return applications;
    }


    @LogExecutionTime
    public ObjectAttributeExtendedClazz parseObjectAttributeNode(Value temp) {
        ObjectAttributeExtendedClazz node = new ObjectAttributeExtendedClazz();

        if (temp == NULL) {

            String entityClass = "OA";
            node.setOEntityClazz("empty");

            node.setType(ClazzType.valueOf(entityClass));
            return node;
        }


        String idString = temp.get("id").asString();
        UUID id = UUID.fromString(idString);
        String name = temp.get("name").asString();
        String entityClass = temp.get("type").asString();

        node.setId(id);
        node.setName(name);
        node.setType(ClazzType.valueOf(entityClass));

        List<Object> readProperties = temp.get("readProperties").asList();
        List<Object> writeProperties = temp.get("writeProperties").asList();
        List<Object> shareReadProperties = temp.get("shareReadProperties").asList();
        List<Object> shareWriteProperties = temp.get("shareWriteProperties").asList();


        node.setReadProperties(listToSetString(readProperties));
        node.setWriteProperties(listToSetString(writeProperties));
        node.setShareReadProperties(listToSetString(shareReadProperties));
        node.setShareWriteProperties(listToSetString(shareWriteProperties));
        node.setDigitsAccess(null);


        return node;
    }


    public DigitAccessClazz parseDigitAccessNode(Value temp) {
        DigitAccessClazz node = new DigitAccessClazz();
        String idString = temp.get("id").asString();
        UUID id = UUID.fromString(idString);
        String name = temp.get("name").asString();
        String entityClass = temp.get("type").asString();
        node.setId(id);
        node.setName(name);
        node.setType(ClazzType.valueOf(entityClass));
        node.setDigitstart(temp.get("digitstart").asInt());
        node.setDigitstop(temp.get("digitstop").asInt());
        node.setProperty(temp.get("property").asString());
        node.setPropertytype(temp.get("propertytype").asString());
        return node;
    }

    public DigitAccessClazz parseDigitAccessNodeFromObject(Object da) {
        DigitAccessClazz node = new DigitAccessClazz();

        InternalNode temp = (InternalNode) da;

        String idString = temp.get("id").asString();
        UUID id = UUID.fromString(idString);
        String name = temp.get("name").asString();
        String entityClass = temp.get("type").asString();
        node.setId(id);
        node.setName(name);
        node.setType(ClazzType.valueOf(entityClass));
        node.setDigitstart(temp.get("digitstart").asInt());
        node.setDigitstop(temp.get("digitstop").asInt());
        node.setProperty(temp.get("property").asString());
        node.setPropertytype(temp.get("propertytype").asString());
        return node;


    }


    public ObjectAttributeClazz parseObjectAttributeNodeFromExtended(ObjectAttributeExtendedClazz temp) {
        ObjectAttributeClazz node = new ObjectAttributeClazz();
        node.setId(temp.getId());
        node.setName(temp.getName());
        node.setType(temp.getType());
        node.setReadProperties(temp.getReadProperties());
        node.setWriteProperties(temp.getWriteProperties());
        node.setShareReadProperties(temp.getShareReadProperties());
        node.setShareWriteProperties(temp.getShareWriteProperties());
        node.setDigitsAccess(temp.getDigitsAccess());
        return node;
    }


}


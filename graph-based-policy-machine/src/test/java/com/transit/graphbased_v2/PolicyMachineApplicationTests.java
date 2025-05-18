package com.transit.graphbased_v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transit.graphbased_v2.controller.dto.AccessResponseDTO;
import com.transit.graphbased_v2.controller.dto.IdentityDTO;
import com.transit.graphbased_v2.controller.dto.OAPropertiesDTO;
import com.transit.graphbased_v2.controller.dto.ObjectDTO;
import com.transit.graphbased_v2.repository.helper.DBClean;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.DatabaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyMachineApplicationTests {


    private TestRestTemplate restTemplate;


    @Autowired
    private DBClean dbClean;


    @LocalServerPort
    private int port;

    @Autowired
    public PolicyMachineApplicationTests(TestRestTemplate testRestTemplate) {
        this.restTemplate = testRestTemplate;
        this.restTemplate.getRestTemplate().setInterceptors(Collections.singletonList((request, body, execution) -> {
            request.getHeaders().add("X-API-KEY", "614D5358726EC07655BF4A38CA751A74C80F42CE571E5055FEF920ECCAC2624C");
            return execution.execute(request, body);
        }));
    }

    private static Set<String> generateRandomSet(String[] source, int numberOfElements) {
        if (source.length < numberOfElements) {
            throw new IllegalArgumentException("Number of elements requested exceeds the number of unique elements available.");
        }

        Set<String> resultSet = new HashSet<>();
        Random random = new Random();

        // Continue adding until the set reaches the desired size
        while (resultSet.size() < numberOfElements) {
            int index = random.nextInt(source.length);
            resultSet.add(source[index]);
        }

        return resultSet;
    }

    private static Set<String> pickRandomElementsFromSet(Set<String> originalSet, int numberOfPicks) {
        if (originalSet.size() < numberOfPicks) {
            throw new IllegalArgumentException("Number of elements requested exceeds the number of unique elements in the set.");
        }

        List<String> tempList = new ArrayList<>(originalSet);
        Set<String> pickedSet = new HashSet<>();
        Random random = new Random();

        while (pickedSet.size() < numberOfPicks) {
            int index = random.nextInt(tempList.size());
            pickedSet.add(tempList.get(index));
            tempList.remove(index); // Remove the element to avoid re-picking
        }

        return pickedSet;
    }

    public static int getRandomIntFromOneToSize(int size) {
        Random random = new Random();
        // nextInt(size) returns a value from 0 (inclusive) to size (exclusive)
        // Add 1 to shift the range from 1 to size (inclusive)
        return random.nextInt(size) + 1;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void createBaseSetup() throws DatabaseException, JsonProcessingException {


        //####Valditation Rules######
        //properties have the new properties to update
        //validation that writeProperties can just be the readProperties or less
        //validation that shareReadProperties can just be readProperties or less
        //validation that shareWriteProperties can just be writeProperties or less

        //validation that readProperties can only be my shareReadProperties (I can only give readProperties which are allowed for me to share)
        //validation that writeProperties can only be my writeReadProperties (I can only give writeProperties which are allowed for me to share)


        String[] source = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"};


        int amountOfLoops = 100;
        for (int a = 0; a < amountOfLoops; a++) {

            log.error("Mainloop: " + a);

            UUID parentIdentityId = UUID.randomUUID();
            createIdentity(parentIdentityId);

            //create Object byI1
            UUID oId = UUID.randomUUID();
            // Generate the random set

            int numberOfProps = getRandomIntFromOneToSize(source.length - 2) + 1;

            if (numberOfProps < 6) {
                numberOfProps = 6;
            }
            Set<String> randomObjectProperties = generateRandomSet(source, numberOfProps);
            createObject(oId, parentIdentityId, randomObjectProperties);

            //should be all objectProps
            OAPropertiesDTO parentOa = new OAPropertiesDTO();
            parentOa.setReadProperties(randomObjectProperties);
            parentOa.setWriteProperties(randomObjectProperties);
            parentOa.setShareReadProperties(randomObjectProperties);
            parentOa.setShareWriteProperties(randomObjectProperties);

            //check if creator has full rights
            AccessResponseDTO accessForIdentity = getAccess(oId, parentIdentityId, parentIdentityId);
            if (!validateGivenRights(parentOa, accessForIdentity.getObjectProperties())) {
                assertEquals(parentOa, accessForIdentity.getObjectProperties());
            }
            UUID currentParentIdentityId = parentIdentityId;
            OAPropertiesDTO currentParentOa = parentOa;
            OAPropertiesDTO currentOaWeWantToGive = parentOa;
            //share rights to other identities and validate given rights


            for (int b = 0; b < getRandomIntFromOneToSize(5) + 2; b++) {
                log.error("START Childloop: " + b + "------------------------------------------------------------------------------");

                UUID identityId = UUID.randomUUID();
                createIdentity(identityId);


                currentOaWeWantToGive = giveRightsForIdentityObject(oId, identityId, currentParentIdentityId, randomObjectProperties);


                //get it from the database
                accessForIdentity = getAccess(oId, identityId, currentParentIdentityId);


                log.error("-----------------------------CALCULATING ACCESS-------------------------------------------------");
                //calculate what the access should look like
                OAPropertiesDTO oaCalulated = calculateAccess(currentOaWeWantToGive, currentParentOa);

                logAccess("CALCULATED ACCESS: " + "object=" + oId + "?identityId=" + identityId + "&requestedById=" + currentParentIdentityId + " with ", oaCalulated);


                //compare if its matching
                if (!validateGivenRights(oaCalulated, accessForIdentity.getObjectProperties())) {
                    assertEquals(oaCalulated, accessForIdentity.getObjectProperties());
                }
                currentParentOa = oaCalulated;
                currentParentIdentityId = identityId;
                log.error("------------------------------------------------------------------------------");
                log.error("END Childloop: " + b + "------------------------------------------------------------------------------");
                log.error("------------------------------------------------------------------------------");

            }


            //sharing rights to the last identity from the creator of the object
            //should be a merged access from creator and the others
            //calculate merged rights
            OAPropertiesDTO oAFromCreatorWeWantToGive = giveRightsForIdentityObject(oId, currentParentIdentityId, parentIdentityId, randomObjectProperties);
            OAPropertiesDTO oaCalulated = calculateAccess(oAFromCreatorWeWantToGive, parentOa);
            OAPropertiesDTO mergeOaForCheck = mergePropertiesOfTwoOa(currentParentOa, oaCalulated);
            log.error("------------------------------------------------------------------------------");
            log.error("------------------------------------------------------------------------------");
            log.error("Check merged access------------------------------------------------ ");
            log.error("------------------------------------------------------------------------------");


            //get it from the database by itselt
            accessForIdentity = getAccess(oId, currentParentIdentityId, currentParentIdentityId);

            //compare if its matching
            if (!validateGivenRights(mergeOaForCheck, accessForIdentity.getObjectProperties())) {
                assertEquals(mergeOaForCheck, accessForIdentity.getObjectProperties());
            }


        }
    }


    public OAPropertiesDTO giveRightsForIdentityObject(UUID oId, UUID identityId1, UUID identityId2, Set<String> randomObjectProperties) throws JsonProcessingException {

        //create access for I2 by I1//////////////////////////////////////////////////////////////////////////////////////////////////////


        //random properties

        int iRandomize = getRandomIntFromOneToSize(randomObjectProperties.size());

        if (iRandomize < 3) {
            iRandomize = iRandomize + 2;
        }

        Set<String> readProp = pickRandomElementsFromSet(randomObjectProperties, iRandomize);
        Set<String> writeProp = pickRandomElementsFromSet(randomObjectProperties, iRandomize - 1);
        Set<String> shareReadProp = pickRandomElementsFromSet(randomObjectProperties, iRandomize - 2);
        Set<String> shareWriteProp = pickRandomElementsFromSet(randomObjectProperties, iRandomize - 2);


        OAPropertiesDTO oAProperties = new OAPropertiesDTO();
        oAProperties.setReadProperties(readProp);
        oAProperties.setWriteProperties(writeProp);
        oAProperties.setShareReadProperties(shareReadProp);
        oAProperties.setShareWriteProperties(shareWriteProp);

        createAccess(oId, identityId1, identityId2, oAProperties);

        return oAProperties;
    }

    private boolean validateGivenRights(OAPropertiesDTO currentOa, OAPropertiesDTO checkOa) {


        if (!currentOa.getReadProperties().equals(checkOa.getReadProperties())) {
            assertEquals(currentOa.getReadProperties(), checkOa.getReadProperties());
            return false;
        }

        if (!currentOa.getWriteProperties().equals(checkOa.getWriteProperties())) {
            assertEquals(currentOa.getWriteProperties(), checkOa.getWriteProperties());
            return false;
        }

        if (!currentOa.getShareReadProperties().equals(checkOa.getShareReadProperties())) {
            assertEquals(currentOa.getShareReadProperties(), checkOa.getShareReadProperties());
            return false;
        }

        if (!currentOa.getShareWriteProperties().equals(checkOa.getShareWriteProperties())) {
            assertEquals(currentOa.getShareWriteProperties(), checkOa.getShareWriteProperties());
            return false;
        }

        return true;
    }

    private void createIdentity(UUID nodeId) {
        IdentityDTO nodeDTO = new IdentityDTO();
        nodeDTO.setId(nodeId);
        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/identity", nodeDTO, Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private void createObject(UUID oId, UUID identityId, Set<String> properties) {
        // Create object for requestbody
        ObjectDTO creationFullyDTO = new ObjectDTO();
        creationFullyDTO.setObjectId(oId);
        creationFullyDTO.setIdentityId(identityId);
        creationFullyDTO.setObjectEntityClass("ExampleObject");
        creationFullyDTO.setProperties(properties);
        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/object", creationFullyDTO, Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private void createAccess(UUID oId, UUID forIdentityId, UUID byIdentityId, OAPropertiesDTO dto) {
        logAccess("CREATE ACCESS: " + "object=" + oId + "?identityId=" + forIdentityId + "&requestedById=" + byIdentityId + " with ", dto);

        ResponseEntity<Void> response = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/access/" + oId + "?identityId=" + forIdentityId + "&requestedById=" + byIdentityId, dto, Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private void updateAccess(UUID oId, UUID uaIdCalling, UUID uaIdTarget, Set<String> props) {
        var dto = new OAPropertiesDTO(props, props, props, props);
        String url = "http://localhost:" + port + "/api/v1/access/" + oId + "?identityId=" + uaIdTarget + "&requestedById=" + uaIdCalling;
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<OAPropertiesDTO> entity = new HttpEntity<>(dto, headers);
        ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private AccessResponseDTO getAccess(UUID oId, UUID forIdentityId, UUID byIdentityId) throws JsonProcessingException {


        String url = "http://localhost:" + port + "/api/v1/access/" + oId + "?identityId=" + forIdentityId + "&requestedById=" + byIdentityId;
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode root = mapper.readTree(response.getBody());


            UUID objectId = UUID.fromString(root.get("objectId").asText());
            String objectEntityClass = root.get("objectEntityClass").asText();
            UUID identityId = UUID.fromString(root.get("identityId").asText());

            JsonNode propsNode = root.get("objectProperties");
            OAPropertiesDTO objectProperties = new OAPropertiesDTO();
            objectProperties.setReadProperties(jsonArrayToSet(propsNode.get("readProperties")));
            objectProperties.setWriteProperties(jsonArrayToSet(propsNode.get("writeProperties")));
            objectProperties.setShareReadProperties(jsonArrayToSet(propsNode.get("shareReadProperties")));
            objectProperties.setShareWriteProperties(jsonArrayToSet(propsNode.get("shareWriteProperties")));

            // Set these values in your DTO
            AccessResponseDTO accessResponse = new AccessResponseDTO();
            accessResponse.setObjectId(objectId);
            accessResponse.setObjectEntityClass(objectEntityClass);
            accessResponse.setIdentityId(identityId);
            accessResponse.setObjectProperties(objectProperties);

            logAccess("GET ACCESS: " + "object=" + oId + "&identityId=" + forIdentityId + "&requestedById=" + byIdentityId + " with ", objectProperties);


            return accessResponse;
        } else {
            throw new RuntimeException();
        }
    }

    private void logAccess(String text, OAPropertiesDTO objectProperties) {
        log.error(text);
        log.error("---->readProperties: " + objectProperties.getReadProperties().toString());
        log.error("---->writeProperties: " + objectProperties.getWriteProperties().toString());
        log.error("---->shareReadProperties: " + objectProperties.getShareReadProperties().toString());
        log.error("---->shareWriteProperties: " + objectProperties.getShareWriteProperties().toString());

    }

    // Helper method to convert JSON array of strings to a Set<String>
    private Set<String> jsonArrayToSet(JsonNode jsonArray) {
        Set<String> resultSet = new HashSet<>();
        if (jsonArray != null && jsonArray.isArray()) {
            for (JsonNode item : jsonArray) {
                resultSet.add(item.asText());
            }
        }
        return resultSet;
    }

    private OAPropertiesDTO calculateAccess(OAPropertiesDTO givenRights, OAPropertiesDTO myRights) {

        //log.error("-calculateAccess");
        //logAccess("---givenRights", givenRights);
        //logAccess("---myRights", myRights);


        // myRights included read, write, share rights which I have
        // givingProperties are the access/rights which I want to give someone

        //####Valditation Rules######
        //properties have the new properties to update
        //validation that writeProperties can just be the readProperties or less
        //validation that shareReadProperties can just be readProperties or less
        //validation that shareWriteProperties can just be writeProperties or less

        //validation that readProperties can only be my shareReadProperties (I can only give readProperties which are allowed for me to share)
        //validation that writeProperties can only be my writeReadProperties (I can only give writeProperties which are allowed for me to share)

        Set<String> givingReadProperties = givenRights.getReadProperties();
        Set<String> givingWriteProperties = givenRights.getWriteProperties();
        Set<String> givingShareReadProperties = givenRights.getShareReadProperties();
        Set<String> givingShareWriteProperties = givenRights.getShareWriteProperties();


        OAPropertiesDTO validatedOaNode = new OAPropertiesDTO();
        Set<String> filteredProperties;

        //readProperties
        filteredProperties = new HashSet<>();
        filteredProperties.addAll(givingReadProperties);
        //remove all which are not in my readProperties
        filteredProperties.retainAll(myRights.getReadProperties());
        //remove all which are not in my shareReadProperties
        filteredProperties.retainAll(myRights.getShareReadProperties());
        validatedOaNode.setReadProperties(filteredProperties);

        //writeProperties
        filteredProperties = new HashSet<>();
        filteredProperties.addAll(givingWriteProperties);
        //remove all which are not in givingReadProperties (because I can't write what I can't read)
        filteredProperties.retainAll(givingReadProperties);
        //remove all which are not in my writeProperties
        filteredProperties.retainAll(myRights.getWriteProperties());
        //remove all which are not in my shareWriteProperties
        filteredProperties.retainAll(myRights.getShareWriteProperties());
        validatedOaNode.setWriteProperties(filteredProperties);

        //shareReadProperties
        filteredProperties = new HashSet<>();
        filteredProperties.addAll(givingShareReadProperties);
        //remove all which are not in the givingReadProperties (because I can't allow more to share than I give to read)
        filteredProperties.retainAll(givingReadProperties);
        //remove all which are not in my shareReadProperties
        filteredProperties.retainAll(myRights.getShareReadProperties());
        validatedOaNode.setShareReadProperties(filteredProperties);

        //shareWriteProperties
        filteredProperties = new HashSet<>();
        filteredProperties.addAll(givingShareWriteProperties);
        //remove all which are not in the givingWriteProperties (because I can't allow more to share than I give to write)
        filteredProperties.retainAll(givingWriteProperties);
        //remove all which are not in my shareReadProperties
        filteredProperties.retainAll(myRights.getShareReadProperties());
        //remove all which are not in my shareWriteProperties
        filteredProperties.retainAll(myRights.getShareWriteProperties());
        //remove all which are not in givingShareReadProperties (because I can't forgive other writeProperties than readProperties)
        filteredProperties.retainAll(givingShareReadProperties);

        validatedOaNode.setShareWriteProperties(filteredProperties);

        return validatedOaNode;
    }


    private OAPropertiesDTO mergePropertiesOfTwoOa(OAPropertiesDTO oa1, OAPropertiesDTO oa2) {
        var readPropertiesTemp = oa2.getReadProperties();
        readPropertiesTemp.addAll(oa1.getReadProperties());
        oa2.setReadProperties(readPropertiesTemp);

        var writePropertiesTemp = oa2.getWriteProperties();
        writePropertiesTemp.addAll(oa1.getWriteProperties());
        oa2.setWriteProperties(writePropertiesTemp);

        var shareReadPropertiesTemp = oa2.getShareReadProperties();
        shareReadPropertiesTemp.addAll(oa1.getShareReadProperties());
        oa2.setShareReadProperties(shareReadPropertiesTemp);

        var shareWritePropertiesTemp = oa2.getShareWriteProperties();
        shareWritePropertiesTemp.addAll(oa1.getShareWriteProperties());
        oa2.setShareWriteProperties(shareWritePropertiesTemp);

        return oa2;
    }


}

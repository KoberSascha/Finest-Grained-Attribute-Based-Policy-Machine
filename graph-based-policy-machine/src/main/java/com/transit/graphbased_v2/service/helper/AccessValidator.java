package com.transit.graphbased_v2.service.helper;

import com.transit.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.transit.graphbased_v2.controller.dto.OAPropertiesDTO;
import com.transit.graphbased_v2.controller.dto.ReadAbleDigitsDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AccessValidator {

    public Set<String> matchPropertiesAgainstDigitAccess() {

        //combine property-lists without duplicates
        Set<String> combinedProperties = new HashSet<>();

        return combinedProperties;
    }


    public ObjectAttributeExtendedClazz combineAllOaForReadingAccess(List<ObjectAttributeExtendedClazz> oAs) {


        // for GET access, combine all giving access for an object from different identities to one object which defines the complete access for this object
        if (oAs == null || oAs.isEmpty()) {
            return null; // Or throw an IllegalArgumentException if an empty list is unexpected
        }

        ObjectAttributeExtendedClazz combinedAccess = oAs.get(0);
        // Iterate over the list starting from the second element
        for (int i = 0; i < oAs.size(); i++) {
            ObjectAttributeExtendedClazz oA = oAs.get(i);
            // Combine properties with the existing combinedAccess object
            combinedAccess = combineAccessPropertiesFromOa(oA, combinedAccess);

        }


        return combinedAccess;
    }

    public ObjectAttributeExtendedClazz combineAllOaWithDigitsAccessForReadingAccess(List<ObjectAttributeExtendedClazz> oAs) {
        // for GET access, combine all giving access for an object from different identities to one object which defines the complete access for this object
        if (oAs == null || oAs.isEmpty()) {
            return null; // Or throw an IllegalArgumentException if an empty list is unexpected
        }

        ObjectAttributeExtendedClazz combinedAccess = oAs.get(0);
        // Iterate over the list starting from the second element
        for (int i = 0; i < oAs.size(); i++) {
            ObjectAttributeExtendedClazz oA = oAs.get(i);

            // Combine properties with the existing combinedAccess object
            combinedAccess = combineAccessPropertiesFromOa(oA, combinedAccess);
            combinedAccess = combineDigitAccessPropertiesFromOa(oA, combinedAccess);
        }


        return combinedAccess;
    }

    public ObjectAttributeExtendedClazz combineDigitAccessPropertiesFromOa(ObjectAttributeExtendedClazz temp1, ObjectAttributeExtendedClazz temp2) {
        Set<DigitsAccessDTO> digitsAccessProperties = new HashSet<>();
        Set<DigitsAccessDTO> digitAccess1 = temp1.getDigitsAccess();
        Set<DigitsAccessDTO> digitAccess2 = temp2.getDigitsAccess();
        digitsAccessProperties = combineDigitAccessPropertiesSets(digitAccess1, digitAccess2);
        temp2.setDigitsAccess(digitsAccessProperties);
        return temp2;
    }


    public Set<DigitsAccessDTO> combineDigitAccessPropertiesSets(Set<DigitsAccessDTO> digitAccess1, Set<DigitsAccessDTO> digitAccess2) {
        // combines two digitAccess


        Set<DigitsAccessDTO> digitsAccessProperties = new HashSet<>();
        digitAccess1.forEach(daNode -> {
            // Check if a matching DigitsAccessDTO is present in digitsAccessParent
            Optional<DigitsAccessDTO> match = digitAccess2.stream().filter(parent -> parent.getProperty().equals(daNode.getProperty()) && parent.getType().equals(daNode.getType())).findFirst();

            if (match.isPresent()) {
                // If it exists, we could take some action such as updating values or just adding it as is

                DigitsAccessDTO dto = match.get();

                Set<ReadAbleDigitsDTO> finalRanges = combineDigitAccessProperties(dto, daNode);
                daNode.setReadableDigits(finalRanges);
                digitsAccessProperties.add(daNode);

            } else {
                // If no matching parent, consider it allowed and add to the results
                digitsAccessProperties.add(daNode);
            }
        });

        return digitsAccessProperties;
    }


    public Set<ReadAbleDigitsDTO> combineDigitAccessProperties(DigitsAccessDTO digitAccess1, DigitsAccessDTO digitAccess2) {
        //combines two sets
        //example
        //access1= [{0,1},{3,6}]
        //access2= [{1,2},{4,5}]
        //result= [{0,6}]

        Set<ReadAbleDigitsDTO> readableDigits1 = digitAccess1.getReadableDigits();
        Set<ReadAbleDigitsDTO> readableDigits2 = digitAccess2.getReadableDigits();

        List<ReadAbleDigitsDTO> mergedValidatedDigits = new ArrayList<>();
        mergedValidatedDigits.addAll(readableDigits1);
        mergedValidatedDigits.addAll(readableDigits2);

        List<ReadAbleDigitsDTO> digitList = new ArrayList<>(mergedValidatedDigits);
        var mergedValidatedDigitsRanges = mergeRanges(digitList);

        //LinkedHashSet for sorting
        Set<ReadAbleDigitsDTO> mergedResult = new LinkedHashSet<>(mergedValidatedDigitsRanges);

        return mergedResult;
    }


    private List<ReadAbleDigitsDTO> mergeRanges(List<ReadAbleDigitsDTO> ranges) {
        if (ranges.isEmpty()) return ranges;

        // Sort by starting points, if equal then by ending points
        ranges.sort(Comparator.comparing(ReadAbleDigitsDTO::getReadableDigitsFrom).thenComparing(ReadAbleDigitsDTO::getReadableDigitsTo));

        List<ReadAbleDigitsDTO> merged = new ArrayList<>();
        ReadAbleDigitsDTO last = ranges.get(0);

        for (int i = 1; i < ranges.size(); i++) {
            ReadAbleDigitsDTO current = ranges.get(i);
            // Adjust the condition to check overlap without extending ranges artificially
            if (current.getReadableDigitsFrom() <= last.getReadableDigitsTo()) { // Check if ranges overlap
                // Update the last's to value to be the maximum of the last's to and current's to
                last.setReadableDigitsTo(Math.max(last.getReadableDigitsTo(), current.getReadableDigitsTo()));
            } else {
                merged.add(last); // Add the last range to the result
                last = current;   // Move to the next range
            }
        }
        merged.add(last);

        // Sort by starting points, if equal then by ending points
        merged.sort(Comparator.comparing(ReadAbleDigitsDTO::getReadableDigitsFrom).thenComparing(ReadAbleDigitsDTO::getReadableDigitsTo));

        return merged;
    }


    public Set<String> getPropertiesByString(ObjectAttributeExtendedClazz oa, String type) {

        Set<String> result = new HashSet<>();

        if ("readProperties".equals(type)) {
            result = oa.getReadProperties();
        }
        if ("writeProperties".equals(type)) {
            result = oa.getWriteProperties();
        }
        if ("shareReadProperties".equals(type)) {
            result = oa.getShareReadProperties();
        }
        if ("shareWriteProperties".equals(type)) {
            result = oa.getShareWriteProperties();
        }
        return result;
    }

    public Set<DigitsAccessDTO> validateFinestGrainedOa(ObjectAttributeExtendedClazz oa) {
        //final check digitsaccess against properties
        //{"a","c"} with "b : {0,2} not possible
        Set<DigitsAccessDTO> validatedDigitAccess = new HashSet<>();
        Set<DigitsAccessDTO> digitsAccess = oa.getDigitsAccess();
        digitsAccess.forEach(child -> {
            var currentType = child.getType();
            Set<String> properties = getPropertiesByString(oa, currentType);
            Set<ReadAbleDigitsDTO> childRanges = child.getReadableDigits();
            if (properties.contains(child.getProperty())) {
                validatedDigitAccess.add(child);
            }
        });
        return validatedDigitAccess;
    }

    public Set<DigitsAccessDTO> validateRightsPropertiesDigitAccessAgainstParentOa(Set<DigitsAccessDTO> digitsAccessChild, Set<DigitsAccessDTO> digitsAccessParent, ObjectAttributeExtendedClazz oa) {
        var digitAccessValidated = validationDigitAccessAgainstParent(digitsAccessChild, digitsAccessParent);
        oa.setDigitsAccess(digitAccessValidated);
        digitAccessValidated = validateFinestGrainedOa(oa);
        return digitAccessValidated;
    }

    public Set<DigitsAccessDTO> validationDigitAccessAgainstParent(Set<DigitsAccessDTO> digitsAccessChild, Set<DigitsAccessDTO> digitsAccessParent) {
        if (digitsAccessChild == null || digitsAccessChild.isEmpty()) {
            return new HashSet<>(digitsAccessParent);
        }

        Set<DigitsAccessDTO> validatedAccess = new HashSet<>();

        digitsAccessChild.forEach(child -> {
            Optional<DigitsAccessDTO> parentOpt = digitsAccessParent.stream().filter(parent -> parent.getProperty().equals(child.getProperty()) && parent.getType().equals(child.getType())).findFirst();

            if (parentOpt.isPresent()) {
                DigitsAccessDTO parent = parentOpt.get();

                Set<ReadAbleDigitsDTO> parentRanges = parent.getReadableDigits();
                Set<ReadAbleDigitsDTO> childRanges = child.getReadableDigits();
                Set<ReadAbleDigitsDTO> validatedRanges = new HashSet<>();

                for (ReadAbleDigitsDTO childRange : childRanges) {
                    for (ReadAbleDigitsDTO parentRange : parentRanges) {
                        int from = Math.max(childRange.getReadableDigitsFrom(), parentRange.getReadableDigitsFrom());
                        int to = Math.min(childRange.getReadableDigitsTo(), parentRange.getReadableDigitsTo());

                        if (from <= to) {
                            validatedRanges.add(new ReadAbleDigitsDTO(from, to));
                        }
                    }
                }

                if (!validatedRanges.isEmpty()) {

                    Set<ReadAbleDigitsDTO> finalRanges = new HashSet<>(validatedRanges);
                    child.setReadableDigits(finalRanges);
                    validatedAccess.add(child);
                }
            } else {
                // No matching parent found; assuming unrestricted if not specified to ignore.
                validatedAccess.add(child);
            }
        });

        return validatedAccess;
    }


    public ObjectAttributeClazz validateRightsPropertiesNonDigitAccess(Set<String> givingReadProperties, Set<String> givingWriteProperties, Set<String> givingShareReadProperties, Set<String> givingShareWriteProperties, ObjectAttributeExtendedClazz myRights) {
        // myRights included read, write, share rights which I have
        // givingProperties are the access/rights which I want to give someone

        //####Valditation Rules######
        //properties have the new properties to update
        //validation that writeProperties can just be the readProperties or less
        //validation that shareReadProperties can just be readProperties or less
        //validation that shareWriteProperties can just be writeProperties or less

        //validation that readProperties can only be my shareReadProperties (I can only give readProperties which are allowed for me to share)
        //validation that writeProperties can only be my writeReadProperties (I can only give writeProperties which are allowed for me to share)


        ObjectAttributeClazz validatedOaNode = new ObjectAttributeClazz();
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

    public Set<String> validateAccessPropertiesAgainstParentProperties(Set<String> childProperties, Set<String> parentProperties) {

        //check that the child can only has the properties that the parent has
        //if properties are decreased, that decreased the properties of the child too
        //retainAll just takes all properties that part of parentProperties
        Set<String> filteredChildProperties = new HashSet<>();
        filteredChildProperties.addAll(childProperties);
        filteredChildProperties.retainAll(parentProperties);
        return filteredChildProperties;
    }

    public Set<String> combineAccessProperties(Set<String> properties1, Set<String> properties2) {

        //combine property-lists without duplicates
        Set<String> combinedProperties = new HashSet<>();
        combinedProperties.addAll(properties1);
        combinedProperties.addAll(properties2);
        return combinedProperties;
    }


    public ObjectAttributeExtendedClazz combineAccessPropertiesFromOa(ObjectAttributeExtendedClazz r, ObjectAttributeExtendedClazz temp) {

        if (r.getId() != null && temp.getId() == null) {
            temp.setId(r.getId());
        }

        temp.setReadProperties(combineAccessProperties(temp.getReadProperties(), r.getReadProperties()));
        temp.setWriteProperties(combineAccessProperties(temp.getWriteProperties(), r.getWriteProperties()));
        temp.setShareReadProperties(combineAccessProperties(temp.getShareReadProperties(), r.getShareReadProperties()));
        temp.setShareWriteProperties(combineAccessProperties(temp.getShareWriteProperties(), r.getShareWriteProperties()));

        return temp;
    }


    public ObjectAttributeClazz generateValidatedOANode(ObjectAttributeClazz oaNode, OAPropertiesDTO oaPropertiesdto, ObjectAttributeExtendedClazz requestingIdentityRights) {

        Set<String> readProperties = oaPropertiesdto.getReadProperties();
        Set<String> writeProperties = oaPropertiesdto.getWriteProperties();
        Set<String> shareReadProperties = oaPropertiesdto.getShareReadProperties();
        Set<String> shareWriteProperties = oaPropertiesdto.getShareWriteProperties();

        ObjectAttributeClazz validationOaNode = validateRightsPropertiesNonDigitAccess(readProperties, writeProperties, shareReadProperties, shareWriteProperties, requestingIdentityRights);

        oaNode.setReadProperties(validationOaNode.getReadProperties());
        oaNode.setWriteProperties(validationOaNode.getWriteProperties());
        oaNode.setShareReadProperties(validationOaNode.getShareReadProperties());
        oaNode.setShareWriteProperties(validationOaNode.getShareWriteProperties());

        return oaNode;
    }
}

package com.transit.graphbased_v2.service.helper;

import com.transit.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class OADeserializer {

    public static String parseDigitsAccessNameFromString(String formattedString) {
        // Assuming the format is always "{'name',readableDigitsFrom,readableDigitsTo}"
        int start = formattedString.indexOf("{'") + 2; // start index of the name
        int end = formattedString.indexOf("',");      // end index of the name

        if (start >= 0 && end > start) {
            return formattedString.substring(start, end);
        } else {
            return "Invalid format"; // or throw an exception
        }
    }

    public static DigitsAccessDTO parseDigitsAccessFromString(String formattedString, String type) {
        // Regex to match the specific format
        Pattern pattern = Pattern.compile("\\{'([^']+)',(\\d+),(\\d+)\\}");
        Matcher matcher = pattern.matcher(formattedString);

        if (matcher.find()) {
            String name = matcher.group(1);
            Integer readableDigitsFrom = Integer.parseInt(matcher.group(2));
            Integer readableDigitsTo = Integer.parseInt(matcher.group(3));
            return new DigitsAccessDTO(name, null, type); // Assuming readableDigits is not provided in the string
        } else {
            throw new IllegalArgumentException("Invalid format");
        }
    }


    public ObjectAttributeExtendedClazz parseOAtoOAExtended(ObjectAttributeClazz oa) {
        ObjectAttributeExtendedClazz oaE = new ObjectAttributeExtendedClazz();
        oaE.setId(oa.getId());
        oaE.setEntityClass(oa.getEntityClass());
        oaE.setDigitsAccess(oa.getDigitsAccess());
        oaE.setReadProperties(oa.getReadProperties());
        oaE.setWriteProperties(oa.getWriteProperties());
        oaE.setShareReadProperties(oa.getShareReadProperties());
        oaE.setShareWriteProperties(oa.getShareWriteProperties());
        return oaE;
    }


}

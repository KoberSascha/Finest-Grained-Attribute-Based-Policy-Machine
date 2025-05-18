package com.transit.graphbased_v2.service.helper;

import com.transit.graphbased_v2.controller.dto.DigitsAccessDTO;
import com.transit.graphbased_v2.controller.dto.ReadAbleDigitsDTO;
import com.transit.graphbased_v2.domain.graph.nodes.DigitAccessClazz;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Component
public class DADeserializer {


    public static Set<DigitsAccessDTO> parseDigitsAccessFromDa(List<DigitAccessClazz> daNodes) {
        Set<DigitsAccessDTO> results = new HashSet<>();

        daNodes.forEach(daNode -> {
            // Find existing DigitsAccessDTO with the same property and propertytype
            DigitsAccessDTO existingDto = results.stream()
                    .filter(dto -> dto.getProperty().equals(daNode.getProperty()) && dto.getType().equals(daNode.getPropertytype()))
                    .findFirst()
                    .orElse(null);

            if (existingDto != null) {
                // Entry found, add ReadAbleDigitsDTO to the existing entry
                ReadAbleDigitsDTO readableDigitsDTO = new ReadAbleDigitsDTO();
                readableDigitsDTO.setReadableDigitsFrom(daNode.getDigitstart());
                readableDigitsDTO.setReadableDigitsTo(daNode.getDigitstop());
                existingDto.getReadableDigits().add(readableDigitsDTO);
            } else {
                // No entry found, create new DigitsAccessDTO and add it to results
                DigitsAccessDTO newDto = new DigitsAccessDTO();
                newDto.setProperty(daNode.getProperty());
                newDto.setType(daNode.getPropertytype());

                Set<ReadAbleDigitsDTO> readableDigits = new HashSet<>();
                ReadAbleDigitsDTO readAbleDigitsDTO = new ReadAbleDigitsDTO();
                readAbleDigitsDTO.setReadableDigitsFrom(daNode.getDigitstart());
                readAbleDigitsDTO.setReadableDigitsTo(daNode.getDigitstop());
                readableDigits.add(readAbleDigitsDTO);
                newDto.setReadableDigits(readableDigits);

                results.add(newDto);
            }
        });

        return results;
    }


}

package com.transit.graphbased_v2.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OAPropertiesDTO {

    private Set<String> readProperties;
    private Set<String> writeProperties;
    private Set<String> shareReadProperties;
    private Set<String> shareWriteProperties;
    private Set<DigitsAccessDTO> digitsAccess;


}

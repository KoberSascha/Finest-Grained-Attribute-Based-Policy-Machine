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
public class DigitsAccessDTO {

    private String property;
    private Set<ReadAbleDigitsDTO> readableDigits;
    private String type;
}

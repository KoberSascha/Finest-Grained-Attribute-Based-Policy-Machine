package com.datatrust.graphbased_v2.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ObjectResponseDTO {
    private UUID objectId;
    private String objectEntityClass;
    private String name;
}

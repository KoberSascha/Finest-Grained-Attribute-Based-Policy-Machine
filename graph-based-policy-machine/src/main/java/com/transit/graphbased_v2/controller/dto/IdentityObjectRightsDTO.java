package com.datatrust.graphbased_v2.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class IdentityObjectRightsDTO {

    private UUID identityId;
    private UUID objectId;
    private String entityClass;

    private UUID sharedFromIdentityId;
    private Set<UUID> sharedToIdentityIds = new HashSet<>();

    /**
     * Rights / properties
     */
    private Set<String> readProperties = new HashSet<>();
    private Set<String> writeProperties = new HashSet<>();
    private Set<String> shareReadProperties = new HashSet<>();
    private Set<String> shareWriteProperties = new HashSet<>();

    /**
     * Digit access rules (can stay empty if you don't populate it yet).
     */
    private Set<DigitsAccessDTO> digitsAccess = new HashSet<>();
}

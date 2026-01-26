package com.datatrust.graphbased_v2.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class AccessEntryDTO {
    private UUID identityId;
    private UUID sharedFromIdentityId;
    private List<UUID> sharedToIdentityIds;
    private OAPropertiesDTO identityProperties;

    // getters/setters/constructors
}

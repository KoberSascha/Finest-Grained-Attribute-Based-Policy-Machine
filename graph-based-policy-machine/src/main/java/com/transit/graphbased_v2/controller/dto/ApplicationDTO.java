package com.transit.graphbased_v2.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationDTO {

    @NotNull(message = "applicationId must not be null")
    private UUID applicationId;

    @NotNull(message = "applicationName must not be null")
    private String applicationName;

    @NotNull(message = "identityId must not be null")
    private UUID identityId;
}

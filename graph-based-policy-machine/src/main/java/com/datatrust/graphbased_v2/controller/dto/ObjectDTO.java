package com.datatrust.graphbased_v2.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ObjectDTO {
    @NotNull
    private UUID objectId;
    @NotBlank
    private String objectEntityClass;
    @NotNull
    private UUID identityId;
    @NotNull
    private Set<@NotBlank String> properties;
}

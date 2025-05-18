package com.transit.graphbased_v2.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ObjectDTO {
	private UUID objectId;
	private String objectEntityClass;
	private UUID identityId;
	private Set<String> properties;
}

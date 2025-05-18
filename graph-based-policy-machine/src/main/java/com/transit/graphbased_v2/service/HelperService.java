package com.transit.graphbased_v2.service;

import com.transit.graphbased_v2.controller.dto.EntityPropertiesDTO;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.exceptions.ForbiddenException;

import java.util.UUID;

public interface HelperService {

    boolean renamePropertyOfEntity(UUID applicationId, UUID requestedById, EntityPropertiesDTO entityPropertiesDTO) throws BadRequestException, ForbiddenException;

    boolean addPropertyOfEntity(UUID applicationId, UUID requestedById, EntityPropertiesDTO entityPropertiesDTO) throws BadRequestException, ForbiddenException;

}

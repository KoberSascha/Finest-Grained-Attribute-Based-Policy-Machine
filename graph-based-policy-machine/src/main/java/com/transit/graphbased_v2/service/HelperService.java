package com.datatrust.graphbased_v2.service;

import com.datatrust.graphbased_v2.controller.dto.EntityPropertiesDTO;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import com.datatrust.graphbased_v2.exceptions.ForbiddenException;

import java.util.UUID;

public interface HelperService {

    boolean renamePropertyOfEntity(UUID applicationId, UUID requestedById, EntityPropertiesDTO entityPropertiesDTO) throws BadRequestException, ForbiddenException;

    boolean addPropertyOfEntity(UUID applicationId, UUID requestedById, EntityPropertiesDTO entityPropertiesDTO) throws BadRequestException, ForbiddenException;

}
